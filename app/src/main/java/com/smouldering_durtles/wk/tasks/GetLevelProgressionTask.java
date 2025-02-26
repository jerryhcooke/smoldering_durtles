/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smouldering_durtles.wk.tasks;

import com.smouldering_durtles.wk.WkApplication;
import com.smouldering_durtles.wk.api.ApiState;
import com.smouldering_durtles.wk.api.model.ApiLevelProgression;
import com.smouldering_durtles.wk.db.AppDatabase;
import com.smouldering_durtles.wk.db.dao.LevelProgressionDao;
import com.smouldering_durtles.wk.db.model.TaskDefinition;
import com.smouldering_durtles.wk.livedata.LiveApiProgress;
import com.smouldering_durtles.wk.livedata.LiveApiState;
import com.smouldering_durtles.wk.livedata.LiveLevelDuration;

import static com.smouldering_durtles.wk.Constants.HOUR;
import static com.smouldering_durtles.wk.util.TextUtil.formatTimestampForApi;

/**
 * Task to fetch any level progression records that have been updated since the last time this task was run.
 */
public final class GetLevelProgressionTask extends ApiTask {
    /**
     * Task priority.
     */
    public static final int PRIORITY = 26;

    /**
     * The constructor.
     *
     * @param taskDefinition the definition of this task in the database
     */
    public GetLevelProgressionTask(final TaskDefinition taskDefinition) {
        super(taskDefinition);
    }

    @Override
    public boolean canRun() {
        return WkApplication.getInstance().getOnlineStatus().canCallApi() && ApiState.getCurrentApiState() == ApiState.OK;
    }

    @Override
    protected void runLocal() {
        final AppDatabase db = WkApplication.getDatabase();
        final LevelProgressionDao levelProgressionDao = db.levelProgressionDao();
        final long lastGetLevelProgressionSuccess = db.propertiesDao().getLastLevelProgressionSyncSuccessDate(HOUR);

        LiveApiProgress.reset(true, "Level progression");

        String uri = "/v2/level_progressions";
        if (lastGetLevelProgressionSuccess != 0) {
            uri += "?updated_after=" + formatTimestampForApi(lastGetLevelProgressionSuccess);
        }

        if (!collectionApiCall(uri, ApiLevelProgression.class, levelProgressionDao::insertOrUpdate)) {
            return;
        }

        db.propertiesDao().setLastApiSuccessDate(System.currentTimeMillis());
        db.propertiesDao().setLastLevelProgressionSyncSuccessDate(System.currentTimeMillis());
        db.taskDefinitionDao().deleteTaskDefinition(taskDefinition);
        LiveApiState.getInstance().forceUpdate();
        LiveLevelDuration.getInstance().forceUpdate();
    }
}
