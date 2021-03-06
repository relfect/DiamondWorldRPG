package net.relfect.task.boss;

import com.eatthepath.uuid.FastUUID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.relfect.task.RPG;
import net.relfect.task.boss.event.BossDeathEvent;
import net.relfect.task.boss.event.BossSpawnEvent;
import net.relfect.task.config.Lang;
import net.relfect.task.mysql.MySql;
import net.relfect.task.spawner.Spawner;
import net.relfect.task.spawner.SpawnerManager;
import net.relfect.task.util.BukkitUtil;
import net.relfect.task.util.JavaUtil;
import net.relfect.task.util.RewardItem;
import net.relfect.task.util.TaskUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.craftbukkit.libs.org.apache.commons.lang3.tuple.Pair;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@FieldDefaults(level = AccessLevel.PROTECTED)
public abstract class Boss {

    final BossType type;
    final Spawner spawner;
    final Map<String, Integer> damagers = Maps.newHashMap();
    UUID id;
    LivingEntity entity;
    int totalDamage;

    public Boss(Spawner spawner, BossType type) {
        this.spawner = spawner;
        this.type = type;
    }

    /*
     * Основной метод при спавне моба:
     * Спавнит моба, применяя дату из типа босса
     * Тригеррит ивенты, и так-же добавляет в мапу живых боссов
     * */
    public void spawn(Location spawn) {
        entity = (LivingEntity) spawn.getWorld().spawnEntity(spawn, type.getData().getType());
        id = entity.getUniqueId();
        type.getData().apply(entity);
        SpawnerManager.get().getAliveBosses().add(this);
        Bukkit.getPluginManager().callEvent(new BossSpawnEvent(this));
    }

    /*
     * Основной метод при специальном удалении босса:
     * Удаляет живого моба если он есть, и так-же
     * даёт знать спавнеру что он был деспавнут
     * */
    public void despawn() {
        if (entity != null) {
            entity.remove();
            spawner.die();
        }
    }

    /*
     * Основной метод при тике босса (1 тик = 20 секунд):
     * Телепортирует босса на точку спавна, если он за макс. радиусом от неё.
     * Если босс имеет способность исцеления, применяет её (Костыль полный)
     * */
    public void onTick() {
        if (isOutsideOfRadius())
            entity.teleport(spawner.getSpawnLocation());
        if(type.getData().getRegen() != 0)
            heal(type.getData().getRegen());
    }

    /*
     * Основной метод при аттаке босса на игрока:
     * Изменяет исходимый урон на применимый в спавнере (включая множители, напр.: зелье силы)
     * */
    public void onAttack(Player player, EntityDamageByEntityEvent event) {
        double damage = spawner.getDamage(), multiplier = 1.0;

        if(entity.hasPotionEffect(PotionEffectType.INCREASE_DAMAGE)) {
            multiplier += entity.getPotionEffect(PotionEffectType.INCREASE_DAMAGE).getAmplifier() + 0.5;
        }

        event.setDamage(damage * multiplier);
    }

    /*
     * Основной метод при смерти босса:
     * 1. Выдача валютной награды всем нападавшим по нанесенному урону
     * 2. Оповещение о смерти босса
     * 3. Выпадение рандомного лута на точке смерти
     * 4. Добавление убийства босса в датабазу
     * */
    public void onDeath() {
        if(!type.getData().isChild())
            spawner.die();

        Map<String, Double> percentage = JavaUtil.calculatePercents(damagers, totalDamage);

        final boolean broadcastable = type.getData().isBroadcastable();

        List<String> broadcast = null;
        if (broadcastable) {
            (broadcast = Lists.newArrayList()).addAll(Lang.BOSS_KILLED_HEADER);
        }

        int count = 0;
        double moneyReward = type.getData().getMoneyReward();

        for (Map.Entry<String, Double> entry : percentage.entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            double earned = 0;

            if (moneyReward != 0) {
                earned = entry.getValue() * moneyReward / 100.0D;
                RPG.getEcon().depositPlayer(op, earned);
            }

            if (broadcastable) {
                if (count == 4) {
                    broadcast.add(Lang.BOSS_KILLED_OTHER.replaceAll("%amount%", String.valueOf(damagers.size() - count)));
                } else if (count < 4) {
                    broadcast.add(Lang.BOSS_TOP_FORMAT
                            .replaceAll("%displayname%", BukkitUtil.getName(op))
                            .replaceAll("%earned%", String.valueOf(earned))
                            .replaceAll("%percentage%", String.valueOf(entry.getValue()))
                            .replaceAll("%damage%", String.valueOf(damagers.get(entry.getKey()))));
                }
            }

            count++;
        }

        TaskUtil.async(() -> {
                    List<Pair<String, Integer>> top = damagers.entrySet().stream().sorted(Comparator.comparingDouble(Map.Entry::getValue)).map(entry -> Pair.of(entry.getKey(), entry.getValue())).collect(Collectors.toList());
                    if (top.size() > 3)
                        top.subList(0, 2);
                    MySql.get().update(
                            "INSERT INTO bosses(ID, DATE, TOP) VALUES" +
                                    "(" +
                                    "'" + FastUUID.toString(id) + "', " +
                                    "'" + System.currentTimeMillis() + "', " +
                                    "'" + JavaUtil.stringify(top, 4, "player", "damage") + "'"
                                    + ")");
                }
        );

        if (broadcastable) {
            broadcast.addAll(Lang.BOSS_KILLED_FOOTER);
            broadcast.replaceAll(str -> str.replaceAll("%money%", String.valueOf(type.getData().getMoneyReward())).replaceAll("%displayname%", spawner.getDisplayName()));
            broadcast.forEach(BukkitUtil::bcs);
        }

        Location location = entity.getLocation();
        List<ItemStack> rewards = type.getData().getRewards().stream().filter(RewardItem::isGood).map(RewardItem::getItem).collect(Collectors.toList());
        rewards.forEach(item -> location.getWorld().dropItemNaturally(location, item));

        Bukkit.getPluginManager().callEvent(new BossDeathEvent(this, rewards));

        SpawnerManager.get().getAliveBosses().remove(this);
    }

    /*
     * Основной метод при получении урона от любого игрока:
     * Сохраняет/повышает тотальный урон по боссу игрока
     * */
    public void onDamage(Player player, double damage) {
        totalDamage += damage;

        String id = player.getName();
        if (!damagers.containsKey(id)) {
            damagers.put(id, (int) damage);
        } else {
            damagers.put(id, (int) (damagers.get(id) + damage));
        }
    }

    /*
     * Проверка на радиус моба от макс. радиуса точки
     * */
    public boolean isOutsideOfRadius() {
        return entity.getLocation().distance(spawner.getSpawnLocation()) >= type.getData().getInactiveRadius();
    }

    /*
     * Основной метод для отхила моба
     * */
    public void heal(double health) {
        BukkitUtil.setHealth(entity, entity.getHealth() + health);
    }

}
