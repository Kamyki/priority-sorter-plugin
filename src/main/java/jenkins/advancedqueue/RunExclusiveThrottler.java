package jenkins.advancedqueue;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Queue.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.advancedqueue.priority.PriorityStrategy;
import jenkins.advancedqueue.sorter.QueueItemCache;

public class RunExclusiveThrottler {

    private static List<String> exclusiveJobs = Collections.synchronizedList(new ArrayList<String>());

    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "Low risk")
    private static int exclusiveJobGroupId = -1;

    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "Low risk")
    private static String exclusiveJobName = "";

    static PriorityConfigurationCallback dummyCallback = new PriorityConfigurationCallback() {

        public PriorityConfigurationCallback setPrioritySelection(
                int priority, int jobGroupId, PriorityStrategy reason) {
            return this;
        }

        public PriorityConfigurationCallback setPrioritySelection(int priority) {
            return this;
        }

        public PriorityConfigurationCallback addDecisionLog(int indent, String log) {
            return this;
        }

        public PriorityConfigurationCallback setPrioritySelection(
                int priority, long sortAsInQueueSince, int jobGroupId, PriorityStrategy reason) {
            return this;
        }
    };

    @Extension
    public static class RunExclusiveRunListener extends RunListener<Run> {

        @Override
        public void onStarted(Run r, TaskListener listener) {
            JobGroup jobGroup = PriorityConfiguration.get().getJobGroup(dummyCallback, r.getParent());
            if (jobGroup != null && jobGroup.isRunExclusive()) {
                exclusiveJobGroupId = jobGroup.getId();
                exclusiveJobName = r.getParent().getName();
                exclusiveJobs.add(exclusiveJobName);
            }
        }

        @Override
        public void onCompleted(Run r, TaskListener listener) {
            exclusiveJobs.remove(r.getParent().getName());
        }
    }

    private static class RunExclusiveMode extends CauseOfBlockage {

        @Override
        public String getShortDescription() {
            return "Run Exclusive (" + exclusiveJobName + ")";
        }
    }

    @Extension
    public static class RunExclusiveDispatcher extends QueueTaskDispatcher {

        @Override
        public CauseOfBlockage canRun(Item item) {
            if (exclusiveJobs.size() > 0) {
                if (QueueItemCache.get().getItem(item.getId()).getJobGroupId() != exclusiveJobGroupId) {
                    return new RunExclusiveMode();
                }
            }
            return null;
        }
    }
}
