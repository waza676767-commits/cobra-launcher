package app.mathhelp;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Checks the inbox about every 15 minutes while the app is closed, so messages and images aren't missed. */
public class SyncJob extends JobService {
    static void schedule(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js.getPendingJob(1) != null) return;
            js.schedule(new JobInfo.Builder(1, new ComponentName(c, SyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build());
        } catch (Exception e) {
            // Never crash the app over the background check; it just won't run.
        }
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        Relay.init(this);
        Relay.q.execute(() -> {
            try { if (!Relay.foreground) Relay.pollOnce(); }
            finally { jobFinished(p, false); }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return true; }
}
