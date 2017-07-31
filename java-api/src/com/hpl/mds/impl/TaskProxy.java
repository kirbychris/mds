/*
 *
 *  Managed Data Structures
 *  Copyright © 2016 Hewlett Packard Enterprise Development Company LP.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  As an exception, the copyright holders of this Library grant you permission
 *  to (i) compile an Application with the Library, and (ii) distribute the 
 *  Application containing code generated by the Library and added to the 
 *  Application during this compilation process under terms of your choice, 
 *  provided you also meet the terms and conditions of the Application license.
 *
 */

package com.hpl.mds.impl;

import com.hpl.mds.*;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.log4j.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class TaskProxy extends Proxy implements Task {
  private static final NativeLibraryLoader NATIVE_LIB_LOADER = NativeLibraryLoader.getInstance();
	
  private static final Logger log = Logger.getLogger(IsoContextProxy.class);

  private static final Proxy.Table<TaskProxy> 
    proxyTable = new Proxy.Table<>(TaskProxy::release);

  private static native void release(long h);
  private static native long defaultTaskHandle();
  private static native long pushNewHandle();
  private static native long push(long h);
  private static native long popHandle();
  private static native long getContext(long h);
  private static native long getParent(long h);
  /*
   * If task a is rerun, task b will also be.
   */
  private static native void addDependent(long a, long b);
  private static native void alwaysRedo(long h);
  private static native void cannotRedo(long h);

  static InheritableThreadLocal<TaskProxy> current_ 
    = new InheritableThreadLocal<TaskProxy>() {
        protected TaskProxy childValue(TaskProxy parentValue) {
          return parentValue == null ? defaultTask() : parentValue;
        };
        protected TaskProxy initialValue() {
          return defaultTask();
        };
      };

  IsoContextProxy inContext = null;
  TaskProxy parent = null;
  private List<Predicate<? super TaskProxy>> onPrepareForRedo_ = null;

  private TaskProxy(long h) {
    super(h, proxyTable);
  }

  long getHandle() {
    return handleIndex_;
  }

  @Override
  void releaseHandleIndex(long index) {
    proxyTable.release(index);
  }

  public static TaskProxy fromHandle(long handleIndex) {
    // System.out.format("Looking up task by handle: %d%n", handleIndex);
    TaskProxy tp =  proxyTable.fromIndex(handleIndex, TaskProxy::new);
    //    System.out.format("Found %s%n", tp);
    return tp;
  }

  @Override
  public String toString() {
    return String.format("Task[%,d (%s)]",
                         handleIndex_,
                         inContext);
  }

  @Override
  public IsoContextProxy getContext() {
    if (inContext == null) {
      inContext = IsoContextProxy.fromHandle(getContext(handleIndex_));
    }
    return inContext;
  }

  public TaskProxy getParent() {
    if (parent == null) {
      parent = fromHandle(getParent(handleIndex_));
    }
    return parent;
  }

  void setContext(IsoContextProxy ctxt) {
    inContext = ctxt;
  }

  public static TaskProxy defaultTask() {
    return fromHandle(defaultTaskHandle());
  }

  public static TaskProxy push() {
    TaskProxy p = current();
    TaskProxy t = fromHandle(pushNewHandle());
    t.setContext(p.getContext());
    t.parent = p;
    noteCurrent(t);
    return t;
  }

  public static void pop() {
   noteCurrent(fromHandle(popHandle()));
  }

  public static long handleOf(Task task) {
    return ((TaskProxy)task).handleIndex_;
  }

  public static TaskProxy current() {
    return current_.get();
  }

  static void noteCurrent(TaskProxy t) {
    /*
     * If this is called, we should be sure it's already set.
     */
    current_.set(t);
  }
  
  @Override
  public void dependsOn(Task... others) {
    for (Task other : others) {
      addDependent(handleOf(other), handleIndex_);
    }
  }

  @Override
  public void dependsOn(Collection<? extends Task> others) {
    for (Task other : others) {
      addDependent(handleOf(other), handleIndex_);
    }
  }

  @Override
  public void alwaysRedo() {
    alwaysRedo(handleIndex_);
  }

  @Override
  public void cannotRedo() {
    cannotRedo(handleIndex_);
    /*
     * We might as well clear the redoableTasks entry for this.  That
     * will both save space and prevent the call to prepareForRedo().
     */
    IsoContextProxy ctxt = getContext();
    Map<TaskProxy, Runnable> taskMap = IsoContextProxy.redoableTasks.get(ctxt);
    if (taskMap != null) {
      taskMap.remove(this);
    }
  }

  public static void run(TaskOption opt, Runnable fn) {
    IsoContextProxy ctxt = IsoContextProxy.current();
    /*
     * If the context isn't publishable, then this task will never be
     * asked to redo, so there's no point in even bothering to create
     * one.  (When we pushed, we'd just get the same one anyway.)
     */
    if (!ctxt.isPublishable()) {
      fn.run();
      return;
    }
    Map<TaskProxy, Runnable> taskMap
      = IsoContextProxy
      .redoableTasks
      .computeIfAbsent(ctxt, (k) -> new ConcurrentHashMap<>());
    TaskProxy t = push();
    taskMap.put(t, fn);
    try {
      if (opt != null) {
        ((TaskOptionImpl)opt).applyChainTo(t);
      }
      fn.run();
    } finally {
      pop();
    }
  }

  @Override
  public void establishAndRun(Runnable fn) {
    // System.out.format("Need to reestablish %s%n", this);
    TaskProxy ct = current();
    if (ct == this)  {
      fn.run();
    } else {
      push(handleIndex_);
      noteCurrent(this);
      try {
        fn.run();
      } finally {
        pop();
      }
    }
  }    
  
  @Override
  public <T> void establishAndAccept(Consumer<? super T> fn, T val) {
    TaskProxy ct = current();
    if (ct == this) {
      fn.accept(val);
    } else {
      push(handleIndex_);
      noteCurrent(this);
      try {
        fn.accept(val);
      } finally {
        pop();
      }
    }
  }
  
  @Override
  public void establishAndAccept(LongConsumer fn, long val) {
    TaskProxy ct = current();
    if (ct == this) {
      fn.accept(val);
    } else {
      push(handleIndex_);
      noteCurrent(this);
      try {
        fn.accept(val);
      } finally {
        pop();
      }
    }
  }
  
  @Override
  public <T> T establishAndGet(Supplier<T> fn) {
    TaskProxy ct = current();
    if (ct == this) {
      return fn.get();
    } else {
      push(handleIndex_);
      noteCurrent(this);
      try {
        return fn.get();
      } finally {
        pop();
      }
    }
  }
  
  @Override
  public <T,R> R establishAndApply(Function<? super T, R> fn, T arg) {
    TaskProxy ct = current();
    if (ct == this) {
      return fn.apply(arg);
    } else {
      push(handleIndex_);
      noteCurrent(this);
      try {
        return fn.apply(arg);
      } finally {
        pop();
      }
    }
  }
  

  void rerun(Runnable fn) {
    establishAndRun(fn);
  }    

  public static <T> TaskComputed<T>
    computedValue(Function<? super T, ? extends T> fn)
  {
    IsoContextProxy ctxt = IsoContextProxy.current();
    if (!ctxt.isPublishable()) {
      return new UnpublishableTaskComputed<>(fn);
    }
    return new PublishableTaskComputed<>(fn);
  }

  static class UnpublishableTaskComputed<T> implements TaskComputed<T> {
    final T val;

    UnpublishableTaskComputed(Function<? super T, ? extends T> fn) {
      val = fn.apply(null);
    }

    @Override
    public void taskComputedIsNotFunctional() {}

    @Override
    public T get() {
      return val;
    }
  }

  /*
   * If there's a redo on the task, it will re-run the function, which
   * will reset the value.  If we redo the parent task, presumably, it
   * will create a new TaskComputed object.
   *
   * TODO:
   *
   * This logic isn't quite right.  It's entirely possible that when
   * doing the redo, a task which didn't call get() the first time
   * sees it there now before the task is rerun.  We can fix this by
   * having an "isValid" flag and saving the redo task here, but then
   * we need to go through and invalidate the value prior to re-dos.
   * This isn't too bad if the task itself is going to be re-run, but
   * if the parent (or a further ancestor) is to be re-run, we'll wind
   * up invalidating a lot of TaskComputed objects that are only going
   * to be dropped on the floor.
   *
   * Alternatively, we can flag the tasks to be re-run and have get()
   * check the flag on its entire parentage chain.
   */
  static class PublishableTaskComputed<T> implements TaskComputed<T> {
    T val = null;
    TaskProxy task;
    IsoContextProxy ctxt = IsoContextProxy.current();
    final int ctxtPublishCount = ctxt.successfulPublishCount.get();

    PublishableTaskComputed(Function<? super T, ? extends T> fn)
    {
      Task.asTask(() -> {
          task = TaskProxy.current();
          val = fn.apply(val);
        });
    }

    @Override
    public void taskComputedIsNotFunctional() {}

    @Override
    public T get() {
      IsoContextProxy c = ctxt;
      if (c != null) {
        /*
         * If it's null, that means that somebody decided that the
         * context has published.
         */
        if (ctxt.successfulPublishCount.get() != ctxtPublishCount) {
          /*
           * If the counts aren't equal, the context was published,
           * and this value is, therefore, fixed.  We no longer need
           * to hold onto the task or context.
           */
          task = null;
          ctxt = null;
        } else {
          /*
           * Otherwise, the calling task depends on the one that
           * computed the value.
           */
          Task.current().dependsOn(task);
        }
      }
      return val;
    }
  }

  boolean prepareForRedo() {
    if (onPrepareForRedo_ == null) {
      return true;
    }
    boolean res = onPrepareForRedo_.stream().allMatch(p->p.test(this));
    onPrepareForRedo_ = null;
    return res;
  }

  boolean needsPrepareForRedo() {
    return onPrepareForRedo_ != null;
  }

  @Override
  synchronized public void onPrepareForRedo(Predicate<? super TaskProxy> pred) {
    if (onPrepareForRedo_ == null) {
      onPrepareForRedo_ = new ArrayList<>();
    }
    onPrepareForRedo_.add(pred);
  }
  
}