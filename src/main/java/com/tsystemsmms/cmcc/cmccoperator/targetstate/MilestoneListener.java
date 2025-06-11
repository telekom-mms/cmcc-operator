package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;

public interface MilestoneListener {
    void onMilestoneReached(Milestone reachedMilestone, Milestone previousMilestone);
}
