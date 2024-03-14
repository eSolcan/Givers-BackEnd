package giversapdc.util;

import java.util.Collections;
import java.util.logging.Logger;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheFactory;
import javax.cache.CacheManager;

public class CacheSupport {

	private static final Logger LOG = Logger.getLogger(CacheSupport.class.getName());

	public Cache cache;
	
	
	public CacheSupport() {
		try {
            CacheFactory cacheFactory = CacheManager.getInstance().getCacheFactory();
            //Map<Object, Object> properties = new HashMap<>();
            //properties.put(GCacheFactory.EXPIRATION_DELTA_MILLIS, EXPIRATION);
            //cache = cacheFactory.createCache(properties);
            cache = cacheFactory.createCache(Collections.emptyMap());
        } catch (CacheException e) {
            System.out.println("Error starting cache. \n" + e.getMessage());
            LOG.severe("Error starting cache. \n" + e.getMessage());
        }
	}
		

}
