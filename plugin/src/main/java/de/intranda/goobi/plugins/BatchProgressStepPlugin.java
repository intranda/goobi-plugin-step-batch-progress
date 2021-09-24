package de.intranda.goobi.plugins;

import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.List;

import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;

import de.sub.goobi.helper.HelperSchritte;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class BatchProgressStepPlugin implements IStepPluginVersion2 {

    @Getter
    private String title = "intranda_step_batch_progress";
    @Getter
    private Step step;
    @Getter
    private String value;
    @Getter
    private boolean allowTaskFinishButtons;
    private String returnPath;

    private Process process;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;
        process = step.getProzess();
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
        // return PluginGuiType.PART;
        // return PluginGuiType.PART_AND_FULL;
        // return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_batch_progress.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret == PluginReturnValue.FINISH;
    }

    @Override
    public PluginReturnValue run() {

        // check if process belongs to a batch, if not
        if (process.getBatch() == null) {
            // process doesn't belong to a batch, continue
            return PluginReturnValue.FINISH;
        }

        List<Step> stepsToClose = new ArrayList<>();
        List<Integer> processesInBatch = ProcessManager.getIDList("batchId=" + process.getBatch().getBatchId());
        for (Integer processId : processesInBatch) {
            if (processId.equals(process.getId())) {
                // skip current process
                continue;
            }

            // check if all other processes of the batch reached this step
            Process otherProcessInBatch = ProcessManager.getProcessById(processId);
            for (Step otherstep : otherProcessInBatch.getSchritte()) {
                if (step.getTitel().equals(otherstep.getTitel())) {
                    // found step to check
                    switch (otherstep.getBearbeitungsstatusEnum()) {
                        case LOCKED:
                            // if no - wait
                            return PluginReturnValue.WAIT;
                        case ERROR:
                        case INFLIGHT:
                        case INWORK:
                        case OPEN:
                            // must be closed at the end
                            stepsToClose.add(otherstep);
                            break;
                        case DEACTIVATED:
                        case DONE:
                        default:
                            // already got further, do nothing
                            break;

                    }
                }
            }
        }

        // we reached this, so we don't have any locked steps
        // TODO call rest api

        // close the step in all other processes
        if (!stepsToClose.isEmpty()) {
            HelperSchritte hs = new HelperSchritte();
            for (Step other : stepsToClose) {
                hs.CloseStepObjectAutomatic(other);
            }
        }

        return PluginReturnValue.FINISH;
    }
}
