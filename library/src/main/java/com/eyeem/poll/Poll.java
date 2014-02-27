package com.eyeem.poll;

import java.util.ArrayList;
import java.util.HashSet;

import android.content.Context;
import android.util.Log;

import com.eyeem.storage.Storage;
import com.eyeem.storage.Storage.List;

/**
 * Base class for polling. Works in combination with {@link Storage}
 * class.
 *
 * @param <T>
 */
public abstract class Poll<T> {

   public static boolean DEBUG = false;

   /**
    * No poll has been made yet so we don't know
    * anything.
    */
   public static final int STATE_UNKNOWN = -1;

   /**
    * Poll is looking good.
    */
   public static final int STATE_OK = 0;

   /**
    * Poll has no content.
    */
   public static final int STATE_NO_CONTENT = 1;

   /**
    * Poll has no content due to a error.
    */
   public static final int STATE_ERROR = 2;

   /**
    * Associated storage.
    */
   protected Storage<T>.List list;

   /**
    * No more items to poll
    */
   protected boolean exhausted;

   /**
    * Last time successful {@link #update(Listener, boolean)} occured.
    * UNIX time ms.
    */
   protected long lastTimeUpdated;

   /**
    * How often should polling happen.
    */
   protected long refreshPeriod;

   /**
    * Represents state in which poll is.
    */
   private int state = STATE_UNKNOWN;

   /**
    * Fetches new items
    * @return
    * @throws Throwable
    */
   protected abstract ArrayList<T> newItems() throws Throwable;

   /**
    * Fetches older items
    * @return
    * @throws Throwable
    */
   protected abstract ArrayList<T> oldItems() throws Throwable;

   /**
    * Fetches all the items, used by syncWithRemote
    * @return
    * @throws Throwable
    */
   protected ArrayList<T> allItems() throws Throwable { throw new NoSuchMethodError("Method not implemented"); };

   /**
    * Appends new items to the {@link #list}
    * @param newItems
    * @param listener
    * @param cleanUp
    * @return
    */
   protected abstract int appendNewItems(ArrayList<T> newItems, boolean cleanUp);

   /**
    * Appends old items to the {@link #list}
    * @param oldItems
    * @param listener
    * @return
    */
   protected abstract int appendOldItems(ArrayList<T> oldItems);

   /**
    * @param ctx
    * @param newCount
    * @return Message associated with successful update.
    */
   protected abstract String getSuccessMessage(Context ctx, int newCount);

   /**
    * Fetch new items task
    */
   private RefreshTask updating;

   /**
    * Fetch older items task
    */
   private RefreshTask fetchingMore;

   /**
    * Indicates whether {@link Poll} should update or not. Default policy
    * is to update if {@link #lastTimeUpdated} happened more than
    * {@link #refreshPeriod} time ago.
    * @return
    */
   public boolean shouldUpdate() {
      return (System.currentTimeMillis() - refreshPeriod) > lastTimeUpdated;
   }

   /**
    * Updates poll if it {@link #shouldUpdate()}
    * @param listener
    */
   public void updateIfNecessary(Listener listener) {
      if (shouldUpdate())
         update(listener, false);
   }

   /**
    * Updates poll always. Please consider using
    * {@link #updateIfNecessary(Listener)}
    * @param listener
    * @param cleanUp
    */
   public synchronized void update(Listener listener, final boolean cleanUp) {
      if (updating == null || updating.working == false) {
         updating = new RefreshTask() {

            @Override
            protected ArrayList<T> doInBackground(Void... params) {
               try {
                  return newItems();
               } catch (Throwable e) {
                  error = e;
                  this.cancel(true);
               }
               return new ArrayList<T>();
            }

            @Override
            public int itemsAction(ArrayList<T> result) throws Throwable {
               return appendNewItems(result, cleanUp);
            }

            @Override
            protected void onSuccess(int result) {
               lastTimeUpdated = System.currentTimeMillis();
               if (list.isEmpty()) {
                  state = STATE_NO_CONTENT;
                  onStateChanged();
               }
               super.onSuccess(result);
            }
            @Override
            protected void onError(Throwable error) {
               if (list.isEmpty()) {
                  state = STATE_ERROR;
                  onStateChanged();
               }
               super.onError(error);
            }
         };
      }
      updating.refresh(listener);
   }

   public synchronized void fetchMore(final Listener listener) {
      if (exhausted) {
         if (listener != null)
            listener.onExhausted();
         return;
      }
      if (fetchingMore == null || fetchingMore.working == false) {
         fetchingMore = new RefreshTask() {

            @Override
            protected ArrayList<T> doInBackground(Void... params) {
               try {
                  return oldItems();
               } catch (Throwable e) {
                  error = e;
                  this.cancel(true);
               }
               return new ArrayList<T>();
            }

            @Override
            public int itemsAction(ArrayList<T> result) throws Throwable {
               return appendOldItems(result);
            }
         };
      }
      fetchingMore.refresh(listener);
   }

   /**
    * Sets the associated {@link List}
    * @param list
    */
   public void setStorage(Storage<T>.List list) { this.list = list; }

   /**
    * Getter for the Poll's associated {@link List}
    * @return
    */
   public Storage<T>.List getStorage() { return list; }

   /**
    * Poll refresh interface
    */
   public interface Listener {
      /**
       * Poll started
       */
      public void onStart();

      /**
       * Poll encountered an error
       * @param error
       */
      public void onError(Throwable error);

      /**
       * Poll was successful
       * @param newCount number of new items
       */
      public void onSuccess(int newCount);

      /**
       * Poll is already polling
       */
      public void onAlreadyPolling();

      /**
       * Poll's state has changed
       * @param state
       */
      public void onStateChanged(int state);

      /**
       * Poll has exhausted.
       */
      public void onExhausted();
   }

   private abstract class RefreshTask extends AsyncTaskCompat<Void, Void, ArrayList<T>> {
      Throwable error;

      HashSet<WeakEqualReference<Listener>> listeners = new HashSet<WeakEqualReference<Listener>>();
      boolean working;
      boolean executed = false;

      public abstract int itemsAction(ArrayList<T> result) throws Throwable;

      @Override
      protected void onPostExecute(ArrayList<T> result) {
         super.onPostExecute(result);

         int addedItemsCount = 0;
         try {
            addedItemsCount = itemsAction(result);
         } catch (Throwable e) {
            error = e;
         }

         if (error != null) {
            onError(error);
         } else if (result != null) {
            onSuccess(addedItemsCount);
         }
         working = false;
      }

      protected void onSuccess(int result) {
         for (WeakEqualReference<Listener> _listener : listeners) {
            Listener listener = _listener.get();
            if (listener != null)
               listener.onSuccess(result);
         }
         listeners.clear();
         if (this == fetchingMore)
            fetchingMore = null;
         if (this == updating)
            updating = null;
      }

      protected void onError(Throwable error) {
         if (DEBUG) Log.w(getClass().getSimpleName(), "onError", error);
         for (WeakEqualReference<Listener> _listener : listeners) {
            Listener listener = _listener.get();
            if (listener != null)
               listener.onError(error);
         }
         listeners.clear();
         if (this == fetchingMore)
            fetchingMore = null;
         if (this == updating)
            updating = null;
      }

      @Override
      protected void onCancelled() {
         super.onCancelled();
         onError(error);
         working = false;
      }

      protected void onStateChanged() {
         for (WeakEqualReference<Listener> _listener : listeners) {
            Listener listener = _listener.get();
            if (listener != null)
               listener.onStateChanged(state);
         }
      }

      synchronized void refresh(Listener listener) {
         if (listener != null) {
            listeners.add(new WeakEqualReference<Listener>(listener));

            if (working) {
               listener.onAlreadyPolling();
            }
         }

         if (!working && !executed) {
            executed = true;
            working = true;
            if (listener != null)
               listener.onStart();
            execute();
         }
      }
   }

   /**
    * @return Poll's state
    */
   public int getState() {
      if (list != null && !list.isEmpty())
         return state = STATE_OK;
      return state;
   }

   /**
    * @return tells whether it's ok to persist list in it's current state
    */
   public boolean okToSave() { return true; }

   /**
    * Sets {@link #lastTimeUpdated} value to 0.
    */
   public void resetLastTimeUpdated() {
      lastTimeUpdated = 0;
   }

   boolean syncOngoing;
   public void syncWithRemote() {
      if (syncOngoing)
         return;
      syncOngoing = true;
      Thread t = new Thread(new Runnable() {
         @Override
         public void run() {
            list.loadSync();
            long lastSyncTime = list.getMeta("lastSyncTime") == null ? 0 : (Long)list.getMeta("lastSyncTime");
            lastTimeUpdated = lastSyncTime;
            if (System.currentTimeMillis() - refreshPeriod < lastSyncTime) {
               syncOngoing = false;
               return; // no sync needed
            }
            try {
               Storage<T>.List transaction = list.transaction();
               transaction.clear();
               transaction.addAll(allItems());
               transaction.commit();
               list.setMeta("lastSyncTime", System.currentTimeMillis());
               list.saveSync();
            } catch (Throwable t) { /*syncFailed*/ }
            syncOngoing = false;
         }
      });
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
   }

   public boolean isPolling() {
      return (updating != null && updating.working) || (fetchingMore != null && fetchingMore.working);
   }

   public void dontUpdateForNext(long milliseconds) {
      long newLastTimeUpdate = System.currentTimeMillis() - milliseconds;
      if (newLastTimeUpdate > lastTimeUpdated)
         lastTimeUpdated = newLastTimeUpdate;
   }
}
