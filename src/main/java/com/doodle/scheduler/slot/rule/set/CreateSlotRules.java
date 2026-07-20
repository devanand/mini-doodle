package com.doodle.scheduler.slot.rule.set;

import java.util.List;

import com.doodle.scheduler.slot.rule.CreateSlotRule;
import org.springframework.stereotype.Component;

/** All rules that apply when creating a slot, in @Order sequence. */
@Component
public class CreateSlotRules extends SlotRuleSet {

    CreateSlotRules(List<CreateSlotRule> rules) {
        super(rules);
    }
}
