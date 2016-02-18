/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehcache.internal.events;

import org.ehcache.config.event.CacheEventDispatcherFactoryConfiguration;
import org.ehcache.config.event.DefaultCacheEventDispatcherConfiguration;
import org.ehcache.events.CacheEventDispatcherFactory;
import org.ehcache.events.CacheEventDispatcher;
import org.ehcache.events.CacheEventDispatcherImpl;
import org.ehcache.spi.ServiceProvider;
import org.ehcache.spi.cache.Store;
import org.ehcache.spi.service.ExecutionService;
import org.ehcache.spi.service.ServiceConfiguration;
import org.ehcache.spi.service.ServiceDependencies;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static org.ehcache.spi.ServiceLocator.findSingletonAmongst;

/**
 * {@link CacheEventDispatcher} implementation that shares a single {@link ExecutorService} for unordered firing
 * between {@link org.ehcache.Cache}s of a given {@link org.ehcache.CacheManager}. For ordered firing, a unique
 * single threaded {@link ExecutorService} is handed to each cache.
 */
@ServiceDependencies(ExecutionService.class)
public class CacheEventDispatcherFactoryImpl implements CacheEventDispatcherFactory {

  private final String defaultThreadPoolAlias;
  private volatile ExecutionService executionService;

  public CacheEventDispatcherFactoryImpl() {
    this.defaultThreadPoolAlias = null;
  }

  public CacheEventDispatcherFactoryImpl(CacheEventDispatcherFactoryConfiguration configuration) {
    this.defaultThreadPoolAlias = configuration.getThreadPoolAlias();
  }

  @Override
  public void start(ServiceProvider serviceProvider) {
    executionService = serviceProvider.getService(ExecutionService.class);
  }

  @Override
  public void stop() {
  }

  @Override
  public <K, V> CacheEventDispatcher<K, V> createCacheEventDispatcher(Store<K, V> store, ServiceConfiguration<?>... serviceConfigs) {
    String threadPoolAlias = defaultThreadPoolAlias;
    DefaultCacheEventDispatcherConfiguration config = findSingletonAmongst(DefaultCacheEventDispatcherConfiguration.class, serviceConfigs);
    if (config != null) {
      threadPoolAlias = config.getThreadPoolAlias();
    }

    ExecutorService orderedExecutor = executionService.getOrderedExecutor(threadPoolAlias, new LinkedBlockingQueue<Runnable>());
    ExecutorService unOrderedExecutor = executionService.getUnorderedExecutor(threadPoolAlias, new LinkedBlockingQueue<Runnable>());

    return new CacheEventDispatcherImpl<K, V>(unOrderedExecutor, orderedExecutor);
  }

  @Override
  public <K, V> void releaseCacheEventDispatcher(CacheEventDispatcher<K, V> cenlService) {
    if (cenlService != null) {
      cenlService.shutdown();
    }
  }

}