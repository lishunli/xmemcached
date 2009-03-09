package net.rubyeye.xmemcached.impl;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import net.rubyeye.xmemcached.HashAlgorithm;
import net.rubyeye.xmemcached.MemcachedSessionLocator;
import net.rubyeye.xmemcached.MemcachedTCPSession;
/**
 * ConnectionFactory instance that sets up a ketama compatible connection.
 *
 * <p>
 * This implementation piggy-backs on the functionality of the
 * <code>DefaultConnectionFactory</code> in terms of connections and queue
 * handling. Where it differs is that it uses both the <code>
 * KetamaNodeLocator</code> and the <code>HashAlgorithm.KETAMA_HASH</code>
 * to provide consistent node hashing.
 *
 * @see http://www.last.fm/user/RJ/journal/2007/04/10/392555/
 *
 * </p>
 */

/**
 * 一致性hash算法，基于TreeMap，直接从spymemcached挪用
 * 
 * @author dennis
 * 
 */
public class KetamaMemcachedSessionLocator implements MemcachedSessionLocator {
	static final int NUM_REPS = 160;

	private SortedMap<Long, MemcachedTCPSession> ketamaSessions = new TreeMap<Long, MemcachedTCPSession>();

	final HashAlgorithm hashAlg;

	public KetamaMemcachedSessionLocator() {
		this.hashAlg = HashAlgorithm.KETAMA_HASH;
	}

	public KetamaMemcachedSessionLocator(HashAlgorithm alg) {
		this.hashAlg = alg;
	}

	public KetamaMemcachedSessionLocator(List<MemcachedTCPSession> list,
			HashAlgorithm alg) {
		super();
		hashAlg = alg;
		buildMap(list, alg);
	}

	private void buildMap(List<MemcachedTCPSession> list, HashAlgorithm alg) {
		for (MemcachedTCPSession session : list) {
			String sockStr = String.valueOf(session.getRemoteSocketAddress());
			/**
			 * 按照spy作者的说法，这里是对KETAMA_HASH做特殊处理，为了复用chunk，具体我并不明白，暂时直接拷贝
			 */
			if (alg == HashAlgorithm.KETAMA_HASH) {
				for (int i = 0; i < NUM_REPS / 4; i++) {
					byte[] digest = HashAlgorithm.computeMd5(sockStr + "-" + i);
					for (int h = 0; h < 4; h++) {
						Long k = ((long) (digest[3 + h * 4] & 0xFF) << 24)
								| ((long) (digest[2 + h * 4] & 0xFF) << 16)
								| ((long) (digest[1 + h * 4] & 0xFF) << 8)
								| (digest[h * 4] & 0xFF);
						ketamaSessions.put(k, session);
					}

				}
			} else {
				for (int i = 0; i < NUM_REPS; i++) {
					ketamaSessions
							.put(hashAlg.hash(sockStr + "-" + i), session);
				}
			}
		}
	}

	long getMaxKey() {
		return ketamaSessions.lastKey();
	}

	@Override
	public MemcachedTCPSession getSessionByKey(String key) {
		long hash = hashAlg.hash(key);
		final MemcachedTCPSession rv;
		if (!ketamaSessions.containsKey(hash)) {
			// Java 1.6 adds a ceilingKey method, but I'm still stuck in 1.5
			// in a lot of places, so I'm doing this myself.
			SortedMap<Long, MemcachedTCPSession> tailMap = ketamaSessions
					.tailMap(hash);
			if (tailMap.isEmpty()) {
				hash = ketamaSessions.firstKey();
			} else {
				hash = tailMap.firstKey();
			}
		}
		rv = ketamaSessions.get(hash);
		return rv;
	}

	@Override
	public void setSessionList(List<MemcachedTCPSession> list) {
		buildMap(list, this.hashAlg);
	}

}
