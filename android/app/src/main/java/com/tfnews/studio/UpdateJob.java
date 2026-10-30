package com.tfnews.studio;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs every few hours in the background: if a new version exists, shows a notification. */
public class UpdateJob extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try {
                Updater.Info inf = Updater.fetchNewer(this);
                if (inf != null) Updater.notifyUpdate(this, inf);
            } catch (Exception ignored) {
            }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
