package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * User: solar
 * Date: 06.11.15
 * Time: 16:22
 */
public interface StateWise<T, Evt> extends WeakListenerHolder<Evt> {
  T state();

  abstract class Stub<T, Evt extends StateWise> extends WeakListenerHolderImpl<Evt> implements StateWise<T, Evt> {
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    protected T state;

    public final T state() {
      lock.readLock().lock();
      try {
        return state;
      }
      finally {
        lock.readLock().unlock();
      }
    }

    public final void state(T newState) {
      lock.writeLock().lock();
      try {
        stateImpl(newState);
      }
      finally {
        lock.writeLock().unlock();
      }
    }

    protected void stateImpl(T newState) {
      if (state == newState)
        return;

      state = newState;
      //noinspection unchecked
      invoke((Evt)this);
    }
  }
}
