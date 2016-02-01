/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dc.jul.extension.rsb.com;

/*
 * #%L
 * JUL Extension RSB Communication
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2015 - 2016 DivineCooperation
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

/**
 *
 * @author mpohling
 */
public class RSBInformerPool {
//public class RSBInformerPool implements Activatable {
//
//	public enum State {
//
//		CONSTRUCTED, INITIALIZED, RUNNING, STANDBY, FATAL_ERROR;
//	};
//
//	protected final Logger logger = LoggerFactory.getLogger(getClass());
//
//	private static RSBInformerPool instance;
//
//	private static final Object ACTIVATION_LOCK = new Object();
//	public static final int DEFAULT_POOL_SIZE = 10;
//	public static final String ROOT_SCOPE = "/";
//
//	private final ArrayList<Informer> informerList;
//	private final Map<WatchDog, Informer> watchDogMap;
//	private int poolPointer;
//	private State state;
//
//	public static synchronized RSBInformerPool getInstance() {
//		if (instance == null) {
//			instance = new RSBInformerPool();
//		}
//		return instance;
//	}
//
//	private RSBInformerPool() {
//		this.informerList = new ArrayList<>();
//		this.watchDogMap = new HashMap<>();
//		this.state = State.CONSTRUCTED;
//	}

//	private void init() {
//		final int size = DEFAULT_POOL_SIZE;
//		Informer<Object> informer;
//
//		synchronized (ACTIVATION_LOCK) {
//			if (state != State.CONSTRUCTED) {
//				logger.warn("Skip initialization. " + getClass().getSimpleName() + " is already initialzed!");
//				return;
//			}
//
//			for (int i = 0; i < size; i++) {
//				try {
//					informer = Factory.getInstance().createInformer(new Scope(ROOT_SCOPE));
//					informerList.add(informer);
//					watchDogMap.put(new WatchDog(informer, Informer.class.getSimpleName() + "[" + i + "]"), informer);
//				} catch (InitializeException | InstantiationException ex) {
//					state = State.FATAL_ERROR;
//					logger.error("Could not init core " + Informer.class.getSimpleName() + "[" + i + "]" + "!", ex);
//					informerList.clear();
//					watchDogMap.clear();
//					return;
//				}
//			}
//			state = State.INITIALIZED;
//		}
//	}
//
//	@Override
//	public void activate() {
//
//		if (state == State.CONSTRUCTED) {
//			init();
//		}
//
//		synchronized (ACTIVATION_LOCK) {
//			logger.debug("Activate core informer.");
//
//			if (state == State.RUNNING) {
//				logger.warn("Skip activation. " + getClass().getSimpleName() + " is already running!");
//				return;
//			}
//
//			if (watchDogMap.isEmpty()) {
//				logger.warn("Skip activation, informerpool is empty!");
//				return;
//			}
//
//			for (WatchDog watchDog : watchDogMap.keySet()) {
//				watchDog.activate();
//			}
//			state = State.RUNNING;
//		}
//	}
//
//	@Override
//	public void deactivate() {
//
//		if (state != State.RUNNING) {
//			logger.warn("Skip informerpool deactivation, because pool is not running!");
//			return;
//		}
//
//		synchronized (ACTIVATION_LOCK) {
//			logger.debug("Deactivate core informer.");
//
//			if (state == State.RUNNING) {
//				logger.warn("Skip deactivation. " + getClass().getSimpleName() + " is on standby!");
//				return;
//			}
//
//			if (watchDogMap.isEmpty()) {
//				logger.warn("Skip deactivation, informerpool is empty!");
//				return;
//			}
//
//			for (WatchDog watchDog : watchDogMap.keySet()) {
//				try {
//					watchDog.deactivate();
//				} catch (InterruptedException ex) {
//					logger.error("Could not deactivate core " + watchDog.getServiceName() + "!.", ex);
//				}
//			}
//			state = State.STANDBY;
//		}
//	}
//
//	@Override
//	public boolean isActive() {
//		return state == State.RUNNING;
//	}
//
//	public void shutdown() {
//		deactivate();
//		informerList.clear();
//		watchDogMap.clear();
//		state = State.CONSTRUCTED;
//	}
//
//	public State getState() {
//		return state;
//	}
//
//	private synchronized Informer getNextInformer() {
//
//		if (poolPointer >= informerList.size()) {
//			poolPointer = 0;
//		}
//		return informerList.get(poolPointer++);
//	}
//
//	public Event send(final Event event) throws RSBException {
//		// logger.debug("Send:" +event.toString()); //TODO mpohling: report this bug. toString not defined here!
//		logger.debug("Event[scope=" + event.getScope() + ", type=" + event.getType() + ", metaData=" + event.getMetaData() + "]");
//		if (watchDogMap.isEmpty()) {
//			throw new RSBException("Skip send invocation, because Informerpool is empty!");
//		}
//
//		if (getState() != State.RUNNING) {
//			throw new RSBException("Skip send invocation, because Informerpool is " + state.name() + " instead " + State.RUNNING.name() + "!");
//		}
//
//		return getNextInformer().send(event);
//	}
//
//	@Override
//	public String toString() {
//		return getClass().getSimpleName() + "[" + ROOT_SCOPE + "]";
//	}
}
