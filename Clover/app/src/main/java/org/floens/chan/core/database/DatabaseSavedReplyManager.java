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
package org.floens.chan.core.database;

import androidx.annotation.AnyThread;

import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.SavedReply;
import org.floens.chan.core.repository.SiteRepository;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Saved replies are posts-password combinations used to track what posts are posted by the app,
 * and used to delete posts.
 */
public class DatabaseSavedReplyManager {
    private static final String TAG = "DatabaseSavedReplyManager";

    private static final long SAVED_REPLY_TRIM_TRIGGER = 250;
    private static final long SAVED_REPLY_TRIM_COUNT = 50;

    private final DatabaseManager databaseManager;
    private final DatabaseHelper helper;

    private final Map<Integer, List<SavedReply>> savedRepliesByNo = new HashMap<>();

    public DatabaseSavedReplyManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    /**
     * Check if the given board-no combination is in the database.<br>
     * This is unlike other methods in that it immediately returns the result instead of
     * a Callable. This method is thread-safe and optimized.
     *
     * @param board  board of the post
     * @param postNo post number
     * @return {@code true} if the post is in the saved reply database, {@code false} otherwise.
     */
    @AnyThread
    public boolean isSaved(Board board, int postNo) {
        synchronized (savedRepliesByNo) {
            if (savedRepliesByNo.containsKey(postNo)) {
                List<SavedReply> items = savedRepliesByNo.get(postNo);
                for (int i = 0; i < items.size(); i++) {
                    SavedReply item = items.get(i);
                    if (item.board.equals(board.code) && item.siteId == board.site.id()) {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    public Callable<Void> load() {
        return () -> {
            databaseManager.trimTable(helper.savedDao, "savedreply",
                    SAVED_REPLY_TRIM_TRIGGER, SAVED_REPLY_TRIM_COUNT);

            final List<SavedReply> all = helper.savedDao.queryForAll();

            synchronized (savedRepliesByNo) {
                savedRepliesByNo.clear();
                for (int i = 0; i < all.size(); i++) {
                    SavedReply savedReply = all.get(i);

                    savedReply.site = SiteRepository.forId(savedReply.siteId);

                    List<SavedReply> list = savedRepliesByNo.get(savedReply.no);
                    if (list == null) {
                        list = new ArrayList<>(1);
                        savedRepliesByNo.put(savedReply.no, list);
                    }

                    list.add(savedReply);
                }
            }
            return null;
        };
    }

    public Callable<Void> clearSavedReplies() {
        return () -> {
            long start = Time.startTiming();
            TableUtils.clearTable(helper.getConnectionSource(), SavedReply.class);
            synchronized (savedRepliesByNo) {
                savedRepliesByNo.clear();
            }
            Time.endTiming("Clear saved replies", start);

            return null;
        };
    }

    public Callable<SavedReply> saveReply(final SavedReply savedReply) {
        return () -> {
            helper.savedDao.create(savedReply);
            synchronized (savedRepliesByNo) {
                List<SavedReply> list = savedRepliesByNo.get(savedReply.no);
                if (list == null) {
                    list = new ArrayList<>(1);
                    savedRepliesByNo.put(savedReply.no, list);
                }

                list.add(savedReply);
            }
            return savedReply;
        };
    }

    public Callable<SavedReply> findSavedReply(final Board board, final int no) {
        return () -> {
            QueryBuilder<SavedReply, Integer> builder = helper.savedDao.queryBuilder();
            List<SavedReply> query = builder.where()
                    .eq("site", board.site.id())
                    .and().eq("board", board.code)
                    .and().eq("no", no).query();
            return query.isEmpty() ? null : query.get(0);
        };
    }
}
