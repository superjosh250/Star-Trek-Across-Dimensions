package UFP.data.scripts;

import UFP.data.campaign.people.GeordiLaForge;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class StarfleetMusuemInteractive extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        PersonAPI geordi = Global.getSector().getImportantPeople().getPerson(GeordiLaForge.PERSON_ID);
        if (geordi != null) {
            dialog.getInteractionTarget().setActivePerson(geordi);
            dialog.getVisualPanel().showPersonInfo(geordi);
        }

        return true;
    }
}