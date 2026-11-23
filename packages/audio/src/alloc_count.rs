//! Phase QA2 — counting allocator for tests (test builds only).
//!
//! Wraps the system allocator and counts `alloc`, `alloc_zeroed` and
//! `realloc` calls made on the current thread while `count_allocs` runs.
//! Deallocation is not counted (QA2 Decision 14: dropping a finished voice
//! inside `render` is allowed). Thread-local state keeps parallel tests from
//! interfering.

use std::alloc::{GlobalAlloc, Layout, System};
use std::cell::Cell;

struct Counting;

thread_local! {
    static ACTIVE: Cell<bool> = const { Cell::new(false) };
    static COUNT: Cell<u64> = const { Cell::new(0) };
}

#[inline]
fn note() {
    let _ = ACTIVE.try_with(|a| {
        if a.get() {
            let _ = COUNT.try_with(|c| c.set(c.get() + 1));
        }
    });
}

unsafe impl GlobalAlloc for Counting {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        note();
        System.alloc(layout)
    }

    unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
        note();
        System.alloc_zeroed(layout)
    }

    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        note();
        System.realloc(ptr, layout, new_size)
    }

    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        System.dealloc(ptr, layout)
    }
}

#[global_allocator]
static GLOBAL: Counting = Counting;

/// Run `f` and return its result with the number of heap allocations
/// (alloc, alloc_zeroed, realloc) it made on this thread.
pub(crate) fn count_allocs<R>(f: impl FnOnce() -> R) -> (R, u64) {
    COUNT.with(|c| c.set(0));
    ACTIVE.with(|a| a.set(true));
    let r = f();
    ACTIVE.with(|a| a.set(false));
    (r, COUNT.with(|c| c.get()))
}

#[test]
fn counter_sees_allocations() {
    let (v, n) = count_allocs(|| vec![1u8; 64]);
    assert_eq!(v.len(), 64);
    assert!(n >= 1, "a vec! must be counted");
    let ((), n) = count_allocs(|| {
        let x = 1 + 1;
        assert_eq!(x, 2);
    });
    assert_eq!(n, 0);
}
