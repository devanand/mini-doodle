package com.doodle.scheduler.slot.rule.set;

import java.util.List;

import com.doodle.scheduler.slot.rule.ModifySlotRule;
import org.springframework.stereotype.Component;

/** All rules that apply when modifying an existing slot, in @Order sequence. */
@Component
public class ModifySlotRules extends SlotRuleSet {

    ModifySlotRules(List<ModifySlotRule> rules) {
        super(rules);
    }
}
