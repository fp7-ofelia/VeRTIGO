package org.flowvisor.message;

import java.util.List;

import org.flowvisor.classifier.FVClassifier;
import org.flowvisor.exceptions.ActionDisallowedException;
import org.flowvisor.flows.FlowIntersect;
import org.flowvisor.flows.SliceAction;
import org.flowvisor.log.FVLog;
import org.flowvisor.log.LogLevel;
import org.flowvisor.slicer.FVSlicer;
import org.openflow.protocol.OFError.OFBadActionCode;
import org.openflow.protocol.OFError.OFFlowModFailedCode;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.action.OFAction;

public class FVFlowMod extends org.openflow.protocol.OFFlowMod implements
		Classifiable, Slicable, Cloneable {

	@Override
	public void classifyFromSwitch(FVClassifier fvClassifier) {
		FVMessageUtil.dropUnexpectedMesg(this, fvClassifier);
	}

	/**
	 * FlowMod slicing
	 *
	 * 1) make sure all actions are ok
	 *
	 * 2) expand this FlowMod to the intersection of things in the given match
	 * and the slice's flowspace
	 */

	@Override
	public void sliceFromController(FVClassifier fvClassifier, FVSlicer fvSlicer) {
		FVLog.log(LogLevel.DEBUG, fvSlicer, "recv from controller: ", this);
		FVMessageUtil.translateXid(this, fvClassifier, fvSlicer);

		// FIXME: sanity check buffer id

		// make sure the list of actions is kosher
		List<OFAction> actionsList = this.getActions();
		try {
			actionsList = FVMessageUtil.approveActions(actionsList, this.match,
					fvClassifier, fvSlicer);
		} catch (ActionDisallowedException e) {
			// FIXME : embed the error code in the ActionDisallowedException and
			// pull it out here
			FVLog.log(LogLevel.WARN, fvSlicer, "EPERM bad actions: ", this);
			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
					OFBadActionCode.OFPBAC_EPERM, this), fvSlicer);
			return;
		}
		// expand this match to everything that intersects the flowspace
		List<FlowIntersect> intersections = fvSlicer.getFlowSpace().intersects(
				fvClassifier.getDPID(), this.match);

		int expansions = 0;
		try {
			OFFlowMod original = this.clone(); // keep an unmodified copy
			int oldALen = FVMessageUtil.countActionsLen(this.getActions());
			this.setActions(actionsList);
			// set new length as a function of old length and old actions length
			this.setLength((short) (getLength() - oldALen + FVMessageUtil
					.countActionsLen(actionsList)));

			for (FlowIntersect intersect : intersections) {

				if (intersect.getFlowEntry().hasPermissions(
						fvSlicer.getSliceName(), SliceAction.WRITE)) {
					expansions++;
					FVFlowMod newFlowMod = (FVFlowMod) this.clone();
					// replace match with the intersection
					newFlowMod.setMatch(intersect.getMatch());
					// update flowDBs
					fvSlicer.getFlowRewriteDB().processFlowMods(original,
							newFlowMod);
					fvClassifier.getFlowDB().processFlowMod(newFlowMod,
							fvClassifier.getDPID(), fvSlicer.getSliceName());
					// actually send msg
					fvClassifier.sendMsg(newFlowMod, fvSlicer);
				}
			}
		} catch (CloneNotSupportedException e) {
			FVLog.log(LogLevel.CRIT, fvSlicer,
					"FlowMod does not implement clone()!?: ", e);
			return;
		}

		if (expansions == 0) {
			FVLog.log(LogLevel.WARN, fvSlicer, "dropping illegal fm: ", this);
			fvSlicer.sendMsg(FVMessageUtil.makeErrorMsg(
					OFFlowModFailedCode.OFPFMFC_EPERM, this), fvSlicer);
		} else
			FVLog.log(LogLevel.DEBUG, fvSlicer, "expanded fm ", expansions,
					" times: ", this);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return super.toString() + ";actions="
				+ FVMessageUtil.actionsToString(this.getActions());
	}
}
