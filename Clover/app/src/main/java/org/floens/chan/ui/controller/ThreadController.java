/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.KeyEvent;
import android.view.LayoutInflater;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.FilterType;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.orm.Filter;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.RefreshUIMessage;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.util.List;

import de.greenrobot.event.EventBus;

import static org.floens.chan.utils.AndroidUtils.dp;

public abstract class ThreadController extends Controller implements
        ThreadLayout.ThreadLayoutCallback,
        ImageViewerController.ImageViewerCallback,
        SwipeRefreshLayout.OnRefreshListener,
        ToolbarNavigationController.ToolbarSearchCallback,
        NfcAdapter.CreateNdefMessageCallback,
        ThreadSlideController.SlideChangeListener {
    private static final String TAG = "ThreadController";

    protected ThreadLayout threadLayout;
    private SwipeRefreshLayout swipeRefreshLayout;

    public ThreadController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);

        navigation.handlesToolbarInset = true;

        threadLayout = (ThreadLayout) LayoutInflater.from(context).inflate(R.layout.layout_thread, null);
        threadLayout.create(this);

        swipeRefreshLayout = new SwipeRefreshLayout(context) {
            @Override
            public boolean canChildScrollUp() {
                return threadLayout.canChildScrollUp();
            }
        };
        swipeRefreshLayout.addView(threadLayout);

        swipeRefreshLayout.setOnRefreshListener(this);

        if (navigation.handlesToolbarInset) {
            int toolbarHeight = getToolbar().getToolbarHeight();
            swipeRefreshLayout.setProgressViewOffset(false, toolbarHeight - dp(40), toolbarHeight + dp(64 - 40));
        }

        view = swipeRefreshLayout;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        threadLayout.destroy();

        EventBus.getDefault().unregister(this);
    }

    public void showSitesNotSetup() {
        threadLayout.getPresenter().showNoContent();
    }

    public abstract void openPin(Pin pin);

    /*
     * Used to save instance state
     */
    public Loadable getLoadable() {
        return threadLayout.getPresenter().getLoadable();
    }

    public void selectPost(int post) {
        threadLayout.getPresenter().selectPost(post);
    }

    @Override
    public boolean onBack() {
        return threadLayout.onBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return threadLayout.sendKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    public void onEvent(Chan.ForegroundChangedMessage message) {
        threadLayout.getPresenter().onForegroundChanged(message.inForeground);
    }

    public void onEvent(RefreshUIMessage message) {
        threadLayout.getPresenter().requestData();
    }

    @Override
    public void onRefresh() {
        threadLayout.refreshFromSwipe();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Loadable loadable = getLoadable();
        String url = null;
        NdefMessage message = null;

        if (loadable != null) {
            url = loadable.site.resolvable().desktopUrl(loadable, null);
        }

        if (url != null) {
            try {
                Logger.d(TAG, "Pushing url " + url + " to android beam");
                NdefRecord record = NdefRecord.createUri(url);
                message = new NdefMessage(new NdefRecord[]{record});
            } catch (IllegalArgumentException e) {
                Logger.e(TAG, "NdefMessage create error", e);
            }
        }

        return message;
    }

    public void presentRepliesController(Controller controller) {
        presentController(controller);
    }

    @Override
    public void openReportController(final Post post) {
        navigationController.pushController(new ReportController(context, post));
    }

    public void selectPostImage(PostImage postImage) {
        threadLayout.getPresenter().selectPostImage(postImage);
    }

    @Override
    public void showImages(List<PostImage> images, int index, Loadable loadable, final ThumbnailView thumbnail) {
        // Just ignore the showImages request when the image is not loaded
        if (thumbnail.getBitmap() != null) {
            final ImageViewerNavigationController imageViewerNavigationController = new ImageViewerNavigationController(context);
            presentController(imageViewerNavigationController, false);
            imageViewerNavigationController.showImages(images, index, loadable, this);
        }
    }

    @Override
    public ThumbnailView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage) {
        return threadLayout.getThumbnail(postImage);
    }

    public void onPreviewCreate(ImageViewerController imageViewerController, PostImage postImage) {
        ThumbnailView thumbnailView = getPreviewImageTransitionView(imageViewerController, postImage);
        if (thumbnailView != null) {
            thumbnailView.hide(false);
        }
    }

    @Override
    public void onBeforePreviewDestroy(ImageViewerController imageViewerController, PostImage postImage) {
    }

    @Override
    public void onPreviewDestroy(ImageViewerController imageViewerController, PostImage postImage) {
        ThumbnailView thumbnail = threadLayout.getThumbnail(postImage);
        if (thumbnail != null) {
            thumbnail.show(false);
        }
    }

    @Override
    public void scrollToImage(PostImage postImage) {
        ThumbnailView focused = threadLayout.getThumbnail(postImage);
        if (focused != null) {
            focused.hide(true);
        } else {
            AndroidUtils.waitForLayout(threadLayout, (v) -> {
                ThumbnailView focused2 = threadLayout.getThumbnail(postImage);
                if (focused2 != null) {
                    focused2.hide(true);
                }
                return true;
            });
        }
        for (ThumbnailView visible : threadLayout.getAllVisibleThumbnails()) {
            if (visible != focused) {
                visible.show(true);
            }
        }

        threadLayout.getPresenter().scrollToImage(postImage, true);
    }

    @Override
    public void showAlbum(List<PostImage> images, int index) {
        if (threadLayout.getPresenter().getChanThread() != null) {
            AlbumViewController albumViewController = new AlbumViewController(context);
            albumViewController.setImages(getLoadable(), images, index, navigation.title);

            if (doubleNavigationController != null) {
                doubleNavigationController.pushController(albumViewController);
            } else {
                navigationController.pushController(albumViewController);
            }
        }
    }

    @Override
    public void onShowPosts() {
    }

    @Override
    public void hideSwipeRefreshLayout() {
        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public Toolbar getToolbar() {
        if (navigationController instanceof ToolbarNavigationController) {
            return navigationController.getToolbar();
        } else {
            return null;
        }
    }

    @Override
    public boolean shouldToolbarCollapse() {
        return !AndroidUtils.isTablet(context) && !ChanSettings.neverHideToolbar.get();
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        threadLayout.getPresenter().onSearchVisibilityChanged(visible);
    }

    @Override
    public void onSearchEntered(String entered) {
        threadLayout.getPresenter().onSearchEntered(entered);
    }

    @Override
    public void openFilterForTripcode(String tripcode) {
        FiltersController filtersController = new FiltersController(context);
        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(filtersController);
        } else {
            navigationController.pushController(filtersController);
        }
        // TODO cleanup
        Filter filter = new Filter();
        filter.type = FilterType.TRIPCODE.flag;
        filter.pattern = tripcode;
        filtersController.showFilterDialog(filter);
    }

    @Override
    public void onSlideChanged() {
        threadLayout.gainedFocus();
    }
}
