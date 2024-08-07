package net.botwithus

import net.botwithus.api.game.hud.inventories.Backpack
import net.botwithus.api.game.hud.inventories.Bank
import net.botwithus.internal.scripts.ScriptDefinition
import net.botwithus.rs3.events.impl.InventoryUpdateEvent
import net.botwithus.rs3.events.impl.SkillUpdateEvent
import net.botwithus.rs3.game.Area
import net.botwithus.rs3.game.Client
import net.botwithus.rs3.game.Coordinate
import net.botwithus.rs3.game.hud.interfaces.Interfaces
import net.botwithus.rs3.game.minimenu.MiniMenu
import net.botwithus.rs3.game.minimenu.actions.SelectableAction
import net.botwithus.rs3.game.movement.Movement
import net.botwithus.rs3.game.movement.NavPath
import net.botwithus.rs3.game.movement.TraverseEvent
import net.botwithus.rs3.game.queries.builders.animations.SpotAnimationQuery
import net.botwithus.rs3.game.queries.builders.characters.NpcQuery
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery
import net.botwithus.rs3.game.queries.builders.items.InventoryItemQuery
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery
import net.botwithus.rs3.game.scene.entities.animation.SpotAnimation
import net.botwithus.rs3.game.scene.entities.characters.npc.Npc
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer
import net.botwithus.rs3.game.scene.entities.`object`.SceneObject
import net.botwithus.rs3.game.skills.Skills
import net.botwithus.rs3.game.vars.VarManager
import net.botwithus.rs3.imgui.NativeInteger
import net.botwithus.rs3.input.GameInput
import net.botwithus.rs3.script.Execution
import net.botwithus.rs3.script.LoopingScript
import net.botwithus.rs3.script.ScriptConsole
import net.botwithus.rs3.script.config.ScriptConfig
import net.botwithus.rs3.util.Regex
import java.util.*
import java.util.concurrent.Callable
import java.util.regex.Pattern

fun withdrawGeodes() {
    Zezimax.botState = Zezimax.ZezimaxBotState.START_GEODE_CRACKER
}

fun geodeCracker() {
    val player = Client.getLocalPlayer()
    if (player == null || Client.getGameState() != Client.GameState.LOGGED_IN) {
        Zezimax.Logger.log("Player not logged in. Delaying execution.")
        Execution.delay(Navi.random.nextLong(2500, 7500))
        return
    }
    val nearestBank = Navi.getNearestBank(player.coordinate)
    if (nearestBank == null) {
        Zezimax.Logger.log("No nearby bank found. Delaying execution.")
        Execution.delay(Navi.random.nextLong(2500, 7500))
        return
    }
    if (!Bank.isOpen()) {
        if (!nearestBank.contains(player.coordinate)) {
            Zezimax.Logger.log("Walking to the nearest bank...")
            Movement.traverse(NavPath.resolve(nearestBank.randomWalkableCoordinate))
            Execution.delay(Navi.random.nextLong(1000, 3000))
        }
        Bank.open()
        Execution.delay(Navi.random.nextLong(1000, 2000))
    }

    while (true) {
        val geodeCount = Bank.getItems().filter { it.id == 44816 }.sumOf { it.stackSize }

        if (Utilities.isItemIdInBank(44816)) {
            Zezimax.Logger.log("Not enough Geodes. Reinitializing...")
            Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
            return
        }
        if (geodeCount >= 10) {
            Bank.withdrawAll(44816)
            Execution.delay(Navi.random.nextLong(1000, 3000))
            Bank.close()
            Execution.delay(Navi.random.nextLong(1000, 3000))

            // Interact with geodes to open them
            val geodeComponent = ComponentQuery.newQuery(1473)
                .componentIndex(5)
                .item(44816)
                .option("Open")
                .results()
                .firstOrNull()

            if (geodeComponent != null && geodeComponent.interact("Open")) {
                Zezimax.Logger.log("Opening Geodes...");

                // Wait a random time between 100 and 400 ms
                Execution.delay(Navi.random.nextLong(300, 800));

                // While the backpack is not full and there are still geodes in the inventory
                while (!Backpack.isFull() && Backpack.getItems().any { it.id == 44816 }) {
                    // Interact with the geode to open it
                    if (geodeComponent.interact("Open")) {
                        Execution.delay(Navi.random.nextLong(180, 400));
                    } else {
                        Zezimax.Logger.log("Couldn't open Geode...");
                        break;
                    }
                }
            } else {
                Zezimax.Logger.log("Couldn't open Geode...");
            }

            // Check for specific items in the inventory and process gems
            Execution.delay(Navi.random.nextLong(1000, 3000))
            processGemsInInventory()

            if (!Bank.isOpen()) {
                Bank.open()
                Execution.delay(Navi.random.nextLong(1500, 3000))
            }
            Bank.depositAll()
            Execution.delay(Navi.random.nextLong(2000, 4000))

        } else {
            Zezimax.Logger.log("Not enough Geodes. Reinitializing...")
            Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
            return
        }
    }
}

fun processGemsInInventory() {
    val inventory = Backpack.getItems()
    val craftingLevels = mapOf(
        1625 to 1,   // Opal
        1627 to 13,  // Jade
        1629 to 16,  // Red Topaz
        1623 to 20,  // Sapphire
        1621 to 27,  // Emerald
        1619 to 34,  // Ruby
        1617 to 41   // Diamond
    )

    for ((gemId, requiredLevel) in craftingLevels) {
        if (Skills.CRAFTING.level >= requiredLevel) {
            while (true) {
                val item = Backpack.getItems().firstOrNull { it.id == gemId }
                if (item == null) break
                processGem(item, gemId)
                Execution.delay(Navi.random.nextLong(1000, 2000))
            }
        }
    }
}

fun processGem(item: net.botwithus.rs3.game.Item, gemId: Int) {
    val gemGetName = Utilities.getNameById(item.id)
    Zezimax.Logger.log("Processing gem: $gemGetName")

    val gemsInInventory = InventoryItemQuery.newQuery(93).ids(gemId).results().count()

    if (gemsInInventory == 1) {
        val craftComponent = ComponentQuery.newQuery(1473)
            .componentIndex(5)
            .item(item.id)
            .option("Craft")
            .results()
            .firstOrNull()

        if (craftComponent != null && craftComponent.interact("Craft")) {
            Execution.delay(Navi.random.nextLong(1000, 2500))
        }
    } else if (gemsInInventory > 1) {
        val craftComponent = ComponentQuery.newQuery(1473)
            .componentIndex(5)
            .item(item.id)
            .option("Craft")
            .results()
            .firstOrNull()

        if (craftComponent != null && craftComponent.interact("Craft")) {
            Execution.delay(Navi.random.nextLong(1000, 2500))

            val cutComponent = ComponentQuery.newQuery(1370)
                .componentIndex(30)
                .results()
                .firstOrNull()

            if (cutComponent != null && cutComponent.interact()) {
                Execution.delay(Navi.random.nextLong(2000, 4000))

                while (Interfaces.isOpen(1251)) {
                    Execution.delay(Navi.random.nextLong(1000, 2400))
                }
            }
        }
    }
}
