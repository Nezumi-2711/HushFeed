package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import kotlin.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class InboxQueryNativeTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private final List<Dialog> dialogs = new ArrayList<>();
    private static final LoadedInboxQuery.Matcher MATCH = new LoadedInboxQuery.Matcher() {
        @Override public boolean notification(Object model) { return model instanceof Notice; }
        @Override public boolean matches(Object model, String query) { return ((Notice) model).text.contains(query); }
    };
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        InboxQueryOwners.resetForTests();
        Settings.INBOX_LOADED_SEARCH.save(true);
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
    }
    @After public void tearDown() {
        InboxQueryOwners.resetForTests();
        for (Dialog dialog : dialogs) dialog.dismiss();
        Settings.INBOX_LOADED_SEARCH.resetToDefault();
        controller.close();
    }

    @Test public void defaultOffPreservesNativePublicationFlagsCompletionAndBackingModels() {
        Settings.INBOX_LOADED_SEARCH.save(false);
        Surface surface = new Surface(false);
        int[] completed = {0};
        Runnable nativeCompletion = () -> completed[0]++;
        surface.diff.autoAccept = true;
        surface.state.nativeSet(Arrays.asList(surface.alpha, surface.beta), true, true, nativeCompletion, 0);
        assertSame(surface.state.LJ, surface.state.published);
        assertTrue(surface.state.first);
        assertTrue(surface.state.second);
        assertSame(nativeCompletion, surface.state.completion);
        assertEquals(0, surface.state.mask);
        assertEquals(1, completed[0]);
        assertFalse(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        assertEquals(LoadedInboxQuery.Phase.IDLE, surface.owner().query.phase());
        assertEquals(1, surface.root.getChildCount());
        assertIdentity(surface.state.LJ, surface.alpha, surface.beta);
    }

    @Test public void queryWaitsForRealDiffAndAlignsNativeIndicesIncludingExternalChunks() {
        Surface surface = new Surface(false);
        Parcelable originalPosition = surface.layout.saved;
        assertTrue(surface.owner().seamsReady());
        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        assertIdentity(surface.state.LJ, surface.alpha, surface.beta);
        assertIdentity(surface.state.published, surface.alpha);
        assertEquals(6, surface.state.mask);
        assertNull(surface.state.completion);
        assertFalse(InboxFilter.allowLoadedRequest(surface.vm));
        assertFalse(InboxFilter.allowLoadedRequest(surface.fragment.LLJJJIL));
        assertFalse(InboxFilter.allowLoadedRequest(surface.adapter.LLJLL));
        InboxFilter.onLoadedIndexedCallback(new Closure(surface.fragment), surface.fragment.helper,
                surface.state.LJ, new Pair<>(1, 2));
        assertEquals("the callback waits for the actual accepted diff", 0, surface.fragment.helper.calls);
        surface.diff.accept(0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertIdentity(surface.fragment.helper.models, surface.header, surface.alpha, surface.footer);
        assertEquals(1, surface.fragment.helper.first);
        assertEquals(1, surface.fragment.helper.last);
        assertTrue(InboxFilter.allowLoadedNotificationClick(surface.holder));
        surface.holder.item = surface.beta;
        assertFalse("a recycled holder cannot dispatch a removed model", InboxFilter.allowLoadedNotificationClick(surface.holder));
        assertSame(surface.beta, surface.holder.getItem());
        assertTrue(InboxQueryNative.query(surface.owner(), "", MATCH));
        assertFalse(InboxFilter.beginLoadedRequest(surface.vm));
        assertNull(surface.layout.restored);
        surface.diff.accept(0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(originalPosition, surface.layout.restored);
        assertIdentity(surface.owner().query.rendered(), surface.header, surface.alpha, surface.beta, surface.footer);
        assertTrue(InboxFilter.beginLoadedRequest(surface.vm));
        InboxFilter.returnedLoadedRequest(surface.vm);
    }

    @Test public void searchBarUsesOnlyTheFrameworkParentOutsideTheTwoChildNativeSpring() {
        Surface surface = new Surface(false);
        assertSame(surface.spring, surface.list.getParent());
        assertSame(surface.outer, surface.spring.getParent());
        assertEquals(2, surface.spring.getChildCount());
        assertEquals("the search bar is a sibling of SpringLayout", 2, surface.outer.getChildCount());
        int width = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY);
        surface.root.measure(width, height);
        surface.root.layout(0, 0, 600, 900);
        assertSame(surface.list, surface.spring.nativeNestedChild());

        Settings.INBOX_LOADED_SEARCH.save(false);
        surface.list.getViewTreeObserver().dispatchOnGlobalLayout();
        assertEquals(1, surface.outer.getChildCount());
        assertEquals(2, surface.spring.getChildCount());
        assertSame(surface.list, surface.spring.nativeNestedChild());
    }

    @Test public void queryRefusesPullAndManualRefreshBeforeFlagsOrAnimationsAndClearRestoresStockRefresh() {
        Surface surface = new Surface(false);
        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        surface.diff.accept(0);
        surface.spring.setRefreshing(true);
        surface.spring.LJ();
        assertFalse(surface.spring.refreshing);
        assertFalse(surface.spring.listenerPending);
        assertEquals(0, surface.spring.listenerCalls);
        assertEquals(0, surface.spring.animations);
        assertEquals(0, surface.spring.requests);
        assertEquals(0, surface.spring.completions);

        NativeSpring unrelated = new NativeSpring(activity);
        unrelated.LJ();
        assertTrue(unrelated.refreshing);
        assertEquals(1, unrelated.animations);

        assertTrue(InboxQueryNative.query(surface.owner(), "", MATCH));
        surface.spring.setRefreshing(true);
        surface.spring.LJ();
        assertFalse("clear must wait for genuine diff acceptance", surface.spring.refreshing);
        assertEquals(0, surface.spring.completions);
        surface.diff.accept(0);
        surface.spring.LJ();
        assertTrue(surface.spring.refreshing);
        assertTrue(surface.spring.listenerPending);
        assertEquals(1, surface.spring.listenerCalls);
        assertEquals(1, surface.spring.animations);
        assertEquals(1, surface.spring.requests);
        surface.spring.nativeComplete();
        assertFalse(surface.spring.refreshing);
        assertFalse(surface.spring.listenerPending);
        assertEquals(1, surface.spring.completions);

        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        surface.spring.refreshing = surface.spring.listenerPending = true;
        surface.spring.setRefreshing(false);
        assertFalse("native stop is never guarded", surface.spring.refreshing);
        assertFalse(surface.spring.listenerPending);
    }

    @Test public void syntheticIndicesKeepTheLiveNativeGateAndNeverInitializeItsLazyHelper() {
        Surface surface = new Surface(false);
        surface.fragment.indicesEnabled = false;
        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        surface.diff.accept(0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, surface.fragment.helperAccesses);
        assertEquals(0, surface.fragment.helper.calls);
        assertTrue(InboxQueryNative.query(surface.owner(), "", MATCH));
        surface.diff.accept(0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, surface.fragment.helperAccesses);

        surface.fragment.indicesEnabled = true;
        surface.fragment.nativeIndexed(surface.state.LJ, new Pair<>(0, 1));
        assertEquals(1, surface.fragment.helperAccesses);
        assertEquals(1, surface.fragment.helper.calls);
        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        surface.diff.accept(0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, surface.fragment.helperAccesses);
        assertEquals(2, surface.fragment.helper.calls);
        assertIdentity(surface.fragment.helper.models, surface.header, surface.alpha, surface.footer);
        assertEquals(1, surface.fragment.helper.first);
        assertEquals(1, surface.fragment.helper.last);

        surface.fragment.indicesEnabled = false;
        assertTrue(InboxQueryNative.query(surface.owner(), "beta", MATCH));
        surface.diff.accept(0);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("a previously captured helper does not bypass a later disabled gate", 2, surface.fragment.helper.calls);
        assertEquals(1, surface.fragment.helperAccesses);
        assertTrue(surface.fragment.gateReads >= 2);
    }

    @Test public void queryRefusesAnAlreadyRunningNativeJobAndDoesNotAffectAnotherRequestOwner() {
        Surface surface = new Surface(false);
        InboxNativeRequestsTest.NativeJob job = new InboxNativeRequestsTest.NativeJob();
        assertTrue(InboxFilter.beginLoadedRequest(surface.vm));
        InboxFilter.onLoadedRequestJob(surface.vm, job);
        InboxFilter.returnedLoadedRequest(surface.vm);
        assertFalse(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        assertIdentity(surface.diff.LJIIJJI, surface.header, surface.alpha, surface.beta, surface.footer);
        job.completed = true;
        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        Object unrelatedVm = new Object();
        assertTrue(InboxFilter.beginLoadedRequest(unrelatedVm));
        InboxFilter.returnedLoadedRequest(unrelatedVm);
        assertFalse(InboxFilter.beginLoadedRequest(surface.vm));
    }

    @Test public void clearUsesLatestUnfilteredNativePublicationWithoutReplayingItsCompletion() {
        Surface surface = new Surface(false);
        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        surface.diff.accept(0);
        int[] completed = {0};
        Runnable nativeCompletion = () -> completed[0]++;
        Notice newest = new Notice("alpha newest");
        surface.state.nativeSet(Arrays.asList(surface.beta, newest), true, false, nativeCompletion, 2);
        assertIdentity(surface.state.published, newest);
        assertIdentity(surface.state.LJ, surface.beta, newest);
        assertSame(nativeCompletion, surface.state.completion);
        assertTrue(surface.state.first);
        assertFalse(surface.state.second);
        assertEquals(2, surface.state.mask);
        surface.diff.accept(0);
        assertEquals(1, completed[0]);
        assertTrue(InboxQueryNative.query(surface.owner(), "", MATCH));
        assertIdentity(surface.state.published, surface.beta, newest);
        assertNull(surface.state.completion);
        surface.diff.accept(0);
        assertEquals(1, completed[0]);
        assertIdentity(surface.state.LJ, surface.beta, newest);
    }

    @Test public void lateVmSharingRestoresOnlyTheQueryWindowAndRetainsBothNativeBackingLists() {
        Surface first = new Surface(false);
        assertTrue(InboxQueryNative.query(first.owner(), "alpha", MATCH));
        first.diff.accept(0);
        Surface second = new Surface(true);
        InboxFilter.onNotificationVmObserved(second.widget, first.vm);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(LoadedInboxQuery.Phase.CLEARING, first.owner().query.phase());
        assertIdentity(first.state.published, first.alpha, first.beta);
        assertEquals(LoadedInboxQuery.Phase.IDLE, second.owner().query.phase());
        assertIdentity(second.state.LJ, second.alpha, second.beta);
        first.diff.accept(0);
        assertEquals(LoadedInboxQuery.Phase.IDLE, first.owner().query.phase());
        assertFalse(first.owner().pagingIsolated());
        assertFalse(InboxQueryNative.query(first.owner(), "alpha", MATCH));
    }

    @Test public void detachRestoresAFreshStockPublicationAndClosesLateDiffOwnership() {
        Surface surface = new Surface(false);
        assertTrue(InboxQueryNative.query(surface.owner(), "alpha", MATCH));
        InboxQueryOwners.Owner owner = surface.owner();
        surface.spring.removeView(surface.list);
        assertEquals(LoadedInboxQuery.Phase.CLOSED, owner.query.phase());
        assertIdentity(surface.state.published, surface.alpha, surface.beta);
        assertEquals(14, surface.state.mask);
        assertNull(surface.state.completion);
        surface.diff.accept(0);
        assertEquals(0, owner.query.committedGeneration());
        assertNull(InboxQueryOwners.ownerForTests(surface.list));
    }

    @Test public void lateSharedStateRestoresTheQueryBeforeTheNewWindowsVmGetterRuns() {
        Surface first = new Surface(false);
        assertTrue(InboxQueryNative.query(first.owner(), "alpha", MATCH));
        first.diff.accept(0);
        Surface second = new Surface(true);
        second.list.state = first.state;
        second.list.adapter = first.adapter;
        second.holder.stub = first.stub;
        InboxFilter.onNotificationRowBound(second.holder);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(LoadedInboxQuery.Phase.CLEARING, first.owner().query.phase());
        assertIdentity(first.state.published, first.alpha, first.beta);
        assertFalse(first.owner().seamsReady());
        assertFalse(second.owner().seamsReady());
        first.diff.accept(0);
        assertEquals(LoadedInboxQuery.Phase.IDLE, first.owner().query.phase());
    }

    @Test public void originalNativeIndexedCallbackFailureKeepsItsExceptionIdentity() {
        RuntimeException failure = new IllegalStateException("native callback failed");
        NativeIndices helper = new NativeIndices();
        helper.failure = failure;
        try {
            InboxFilter.onLoadedIndexedCallback(new Closure(new Object()), helper,
                    Collections.singletonList(new Object()), new Pair<>(0, 0));
            fail("the native callback failure must propagate");
        } catch (RuntimeException expected) { assertSame(failure, expected); }
        assertEquals(1, helper.calls);
    }

    private final class Surface {
        final FrameLayout root = new FrameLayout(activity);
        final FrameLayout outer = new FrameLayout(activity);
        final NativeSpring spring = new NativeSpring(activity);
        final NativeState state = new NativeState();
        final NativeLayout layout = new NativeLayout();
        final NativeDiff diff = new NativeDiff();
        final NativeAdapter adapter = new NativeAdapter(diff);
        final NativeList list = new NativeList(activity, state, adapter, layout);
        final NativeStub stub = new NativeStub(state, adapter);
        final NativeFragment fragment = new NativeFragment(root, list, spring);
        final Notice alpha = new Notice("alpha"), beta = new Notice("beta");
        final Object header = new Object(), footer = new Object(), vm = new Object();
        final NativeWidget widget = new NativeWidget(fragment.LLJJJIL);
        final TextView row = new TextView(activity);
        final NativeHolder holder = new NativeHolder(this);
        Surface(boolean secondWindow) {
            state.surface = this; adapter.stub = stub;
            state.LJ.addAll(Arrays.asList(alpha, beta));
            diff.LJIIJJI = aggregate(state.LJ);
            list.addView(row);
            spring.requestTarget = vm;
            spring.addView(new View(activity));
            spring.addView(list, new FrameLayout.LayoutParams(-1, -1));
            outer.addView(spring, new FrameLayout.LayoutParams(-1, -1));
            root.addView(outer, new FrameLayout.LayoutParams(-1, -1));
            if (secondWindow) {
                Dialog dialog = new Dialog(activity);
                dialog.setContentView(root); dialog.show(); dialogs.add(dialog);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertTrue(list.isAttachedToWindow());
            } else activity.setContentView(root);
            InboxFilter.onNotificationRowBound(holder);
            InboxFilter.onNotificationVmObserved(widget, vm);
        }
        InboxQueryOwners.Owner owner() { return InboxQueryOwners.ownerForTests(list); }
        List<?> aggregate(List<?> models) {
            List<Object> all = new ArrayList<>();
            all.add(header); all.addAll(models); all.add(footer); return all;
        }
    }
    private static final class Notice {
        final String text;
        Notice(String text) { this.text = text; }
        @Override public boolean equals(Object other) { return other instanceof Notice; }
        @Override public int hashCode() { return 1; }
    }
    public static final class NativeState {
        public final List<Object> LJ = new ArrayList<>();
        public final ReentrantLock LJFF = new ReentrantLock();
        Surface surface;
        List<?> published;
        boolean first, second;
        Runnable completion;
        int mask;
        public static void LIZ(NativeState state, List<?> models, boolean first, boolean second, Runnable completion, int mask) {
            List<?> projected = InboxFilter.onLoadedPublication(state, models, first, second, completion, mask);
            state.published = projected; state.first = first; state.second = second; state.completion = completion; state.mask = mask;
            Object metadata = new Object();
            InboxFilter.onLoadedStubPublication(state.surface.stub, new Wrapper(projected), metadata);
            state.surface.diff.submit(metadata, state.surface.aggregate(projected), (mask & 8) == 0 ? completion : null);
        }
        void nativeSet(List<?> models, boolean first, boolean second, Runnable completion, int mask) {
            LJFF.lock();
            try { LJ.clear(); LJ.addAll(models); }
            finally { LJFF.unlock(); }
            LIZ(this, LJ, first, second, completion, mask);
        }
    }
    public static final class Wrapper {
        public final List<?> LIZ;
        Wrapper(List<?> models) { LIZ = models; }
    }
    public static final class NativeDiff {
        public int LJIIJ;
        public List<?> LJIIJJI = Collections.emptyList();
        final List<Pending> pending = new ArrayList<>();
        boolean autoAccept;
        void submit(Object metadata, List<?> models, Runnable completion) {
            LJIIJ++;
            InboxFilter.onLoadedDiffSubmitted(this, Collections.emptyList(), LJIIJJI, models, metadata);
            pending.add(new Pending(metadata, models, completion));
            if (autoAccept) accept(pending.size() - 1);
        }
        void accept(int index) {
            Pending accepted = pending.remove(index);
            LJIIJJI = accepted.models;
            InboxFilter.onLoadedDiffAccepted(this, accepted.metadata);
            if (accepted.completion != null) accepted.completion.run();
        }
    }
    private static final class Pending {
        final Object metadata;
        final List<?> models;
        final Runnable completion;
        Pending(Object metadata, List<?> models, Runnable completion) { this.metadata = metadata; this.models = models; this.completion = completion; }
    }
    public static final class NativeAdapter {
        public final NativeDiff LLLII;
        public final Object LLJLL = new Object();
        NativeStub stub;
        NativeAdapter(NativeDiff diff) { LLLII = diff; }
        public NativeStub LLLLLJIL() { return stub; }
    }
    public static final class NativeList extends FrameLayout {
        NativeState state;
        NativeAdapter adapter;
        final NativeLayout layout;
        NativeList(Activity activity, NativeState state, NativeAdapter adapter, NativeLayout layout) {
            super(activity); this.state = state; this.adapter = adapter; this.layout = layout;
        }
        public NativeState getState() { return state; }
        public NativeAdapter getAdapter() { return adapter; }
        public NativeLayout getLayoutManager() { return layout; }
        public void scrollToPosition(int position) {}
    }
    public static final class NativeLayout {
        public final Parcelable saved = new Bundle();
        Parcelable restored;
        public Parcelable onSaveInstanceState() { return saved; }
        public void onRestoreInstanceState(Parcelable state) { restored = state; }
        public int findFirstCompletelyVisibleItemPosition() { return 1; }
        public int findLastCompletelyVisibleItemPosition() { return 1; }
        public int findLastVisibleItemPosition() { return 1; }
    }
    public static final class NativeStub {
        public final NativeAdapter LLJJIJIIJIL;
        final NativeState state;
        NativeStub(NativeState state, NativeAdapter adapter) { this.state = state; LLJJIJIIJIL = adapter; }
        public NativeState LJIIZILJ() { return state; }
    }
    public static final class NativeFragment {
        final View root;
        final NativeList list;
        final NativeSpring spring;
        public final Object LLJJJIL = new Object();
        final NativeIndices helper = new NativeIndices();
        boolean indicesEnabled = true;
        int helperAccesses, gateReads;
        NativeFragment(View root, NativeList list, NativeSpring spring) { this.root = root; this.list = list; this.spring = spring; }
        public View getView() { return root; }
        public NativeList cY() { return list; }
        public NativeSpring bY() { return spring; }
        public NativeIndices ZX() { helperAccesses++; return helper; }
        public boolean hushLoadedInboxIndicesEnabled() { gateReads++; return indicesEnabled; }
        void nativeIndexed(List<?> models, Pair<Integer, Integer> pair) {
            if (indicesEnabled) InboxFilter.onLoadedIndexedCallback(new Closure(this), ZX(), models, pair);
        }
    }
    public static final class NativeSpring extends FrameLayout {
        boolean refreshing, listenerPending;
        int listenerCalls, animations, requests, completions;
        Object requestTarget;
        NativeSpring(Activity activity) { super(activity); }
        View nativeNestedChild() {
            if (getChildCount() > 2) throw new IllegalArgumentException("SpringLayout has more than two children");
            return getChildAt(getChildCount() - 1);
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            nativeNestedChild();
            super.onLayout(changed, left, top, right, bottom);
        }
        public void setRefreshing(boolean value) {
            if (!InboxFilter.allowLoadedRefresh(this, value) || refreshing == value) return;
            refreshing = value;
            if (value && !listenerPending) {
                listenerPending = true; listenerCalls++;
                if (requestTarget != null && InboxFilter.beginLoadedRequest(requestTarget)) {
                    requests++; InboxFilter.returnedLoadedRequest(requestTarget);
                }
            } else if (!value) listenerPending = false;
        }
        public void LJ() {
            if (!InboxFilter.allowLoadedManualRefresh(this)) return;
            setRefreshing(true);
            animations++;
        }
        void nativeComplete() { completions++; setRefreshing(false); }
    }
    public static final class NativeIndices {
        int calls, first, last;
        RuntimeException failure;
        List<?> models;
        public void LJFF(List<?> models, Pair<Integer, Integer> positions) {
            calls++;
            if (failure != null) throw failure;
            this.models = models; first = positions.getFirst(); last = positions.getSecond();
            assertTrue(first >= -1 && last < models.size());
        }
    }
    public static final class NativeHolder {
        final Surface surface;
        public final View itemView;
        NativeStub stub;
        Object item;
        NativeHolder(Surface surface) { this.surface = surface; itemView = surface.row; item = surface.alpha; stub = surface.stub; }
        public View getAttachView$powerlist_release() { return surface.list; }
        public NativeAdapter getAdapterManager$powerlist_release() { return surface.list.adapter; }
        public NativeFragment getCurFragment() { return surface.fragment; }
        public NativeStub getStub() { return stub; }
        public Object getItem() { return item; }
    }
    public static final class NativeWidget {
        final Object parent;
        NativeWidget(Object parent) { this.parent = parent; }
        public Object getParent() { return parent; }
    }
    public static final class Closure {
        public final Object l0;
        Closure(Object fragment) { l0 = fragment; }
    }
    private static void assertIdentity(List<?> actual, Object... expected) {
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) assertSame("model at " + i, expected[i], actual.get(i));
    }
}
