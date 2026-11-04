package com.aicoach.task;

import java.util.Collection;

/** Share of reported work actually done, so every feature measures progress the same way. */
public final class Completion {

    private Completion() {
    }

    /** (done minutes + half of partial minutes) / reported minutes; pending tasks are ignored, null if none. */
    public static Double rate(Collection<Task> tasks) {
        double reported = 0;
        double achieved = 0;
        for (Task t : tasks) {
            if (t.getStatus() == TaskStatus.PENDING) continue;
            reported += t.getEstimatedMinutes();
            if (t.getStatus() == TaskStatus.DONE) achieved += t.getEstimatedMinutes();
            if (t.getStatus() == TaskStatus.PARTIAL) achieved += t.getEstimatedMinutes() / 2.0;
        }
        return reported == 0 ? null : achieved / reported;
    }
}
