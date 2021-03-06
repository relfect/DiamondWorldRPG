package net.relfect.task.boss.event;

import lombok.Getter;
import net.relfect.task.boss.Boss;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@Getter
public class BossDeathEvent extends EntityDeathEvent {

    private static final HandlerList handlers = new HandlerList();
    private final Boss boss;

    public BossDeathEvent(Boss boss, List<ItemStack> drops) {
        super(boss.getEntity(), drops);
        this.boss = boss;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

