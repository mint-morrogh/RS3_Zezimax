package net.botwithus

import net.botwithus.api.game.hud.inventories.Backpack
import net.botwithus.api.game.hud.inventories.Bank
import net.botwithus.internal.scripts.ScriptDefinition
import net.botwithus.rs3.events.impl.InventoryUpdateEvent
import net.botwithus.rs3.events.impl.SkillUpdateEvent
import net.botwithus.rs3.game.Area
import net.botwithus.rs3.game.Client
import net.botwithus.rs3.game.Coordinate
import net.botwithus.api.game.hud.Dialog
import net.botwithus.rs3.game.hud.interfaces.Dialog as DialogNext
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


val featherId = 314
val baitId = 313
val fishingGetName = Utilities.getNameById(DecisionTree.fishToCollect)

fun withdrawFishingSupplies() {
    if (!Bank.isOpen()) {
        Execution.delay(Navi.random.nextLong(1300, 2000))
        Bank.open()
        Execution.delay(Navi.random.nextLong(1300, 2000))
    }

    if (Bank.isOpen()) {
        // Define item IDs for feathers and bait

        // Withdraw feathers if needed
        if (DecisionTree.feathersNeeded) {
            if (Utilities.isItemIdInBank(featherId)) {
                Bank.withdrawAll(featherId)
                Zezimax.Logger.log("Feathers withdrawn...")
                Execution.delay(Navi.random.nextLong(1300, 2000))
            }
            else {
                Zezimax.Logger.log("Out of feathers, Reinitializing...")
                Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
                return
            }
        }

        // Withdraw bait if needed
        if (DecisionTree.baitNeeded) {
            if (Utilities.isItemIdInBank(baitId)) {
                Bank.withdrawAll(baitId)
                Zezimax.Logger.log("Bait withdrawn...")
                Execution.delay(Navi.random.nextLong(1300, 2000))
            }
            else {
                Zezimax.Logger.log("Out of bait, Reinitializing...")
                Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
                return
            }
        }

        // Close the bank after withdrawing items
        Bank.close()
        Execution.delay(Navi.random.nextLong(1300, 2000))

        // Move to the start fishing state
        Zezimax.botState = Zezimax.ZezimaxBotState.START_FISHING
    } else {
        Zezimax.Logger.log("Bank is not open")
    }
}

class Fishing(private val locationFish: String,
             private val locationBank: String,
             private val spotName: String,
             private val fishName: Int,
             private val fishUntil: Int
) {

    // LOCATIONS
    fun navigateToFishingLocation() {
        when (locationFish) {
            "BarbarianVillageFishing" -> Navi.walkToBarbarianVillageFishing()
            "AlkharidWestFishing" -> Navi.walkToAlkharidWestFishing()
            "PortSarimFishing" -> {
                // If location is Port Sarim, handle fare payment and navigation
                Navi.walkToPortSarimPayFare()
                Navi.random.nextLong(2000, 3000)
                val tobias: Npc? = NpcQuery.newQuery().option("Pay fare").results().nearest()
                if (tobias != null && tobias.interact("Pay fare")) {
                        Zezimax.Logger.log("Paying for boat ride...")
                        Navi.random.nextLong(4000, 6400)
                }
                Navi.random.nextLong(1000, 2400)
                DialogNext.next()
                Navi.random.nextLong(1000, 2400)
                Dialog.interact("Yes please.")
                Navi.random.nextLong(1000, 2400)
                DialogNext.next()
                Navi.random.nextLong(25000, 30000) // Wait for the cutscene to finish
                Navi.walkToPortSarimFishing()
            }
            // Add more cases as needed
            else -> throw IllegalArgumentException("Unknown location: $locationFish")
        }
    }

    fun navigateToBankLocation() {
        when (locationBank) {
            "EdgevilleBank" -> Navi.walkToEdgevilleBank()
            "AlkharidWestBank" -> Navi.walkToAlkharidWestBank()
            "DraynorBank" -> Navi.walkToDraynorBank()
            "FaladorSouthBank" -> Navi.walkToFaladorSouthBank()
            // Add more cases as needed
            else -> throw IllegalArgumentException("Unknown bank location: $locationBank")
        }
    }

    fun fish(player: LocalPlayer) {
        Execution.delay(Navi.random.nextLong(1000, 2000))
        navigateToFishingLocation()

        while (true) {
            // Fishing
            while (!Backpack.isFull()) {
                // Check if feathers are needed and if they are present in inventory
                if (DecisionTree.feathersNeeded) {
                    val featherItem = InventoryItemQuery.newQuery(93).ids(featherId).results().firstOrNull()
                    if (featherItem != null) {
                        if (featherItem.stackSize > 0) {
                            Zezimax.Logger.log("Feathers still in inventory, continuing to fish...")
                        } else {
                            Zezimax.Logger.log("Out of feathers, Reinitializing...")
                            Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
                            return
                        }
                    }
                }
                // Check if bait is needed and if they are present in inventory
                if (DecisionTree.baitNeeded) {
                    val baitItem = InventoryItemQuery.newQuery(93).ids(baitId).results().firstOrNull()
                    if (baitItem != null) {
                        if (baitItem.stackSize > 0) {
                            Zezimax.Logger.log("Bait still in inventory, continuing to fish for $fishingGetName...")
                        } else {
                            Zezimax.Logger.log("Out of bait, Reinitializing...")
                            Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
                            return
                        }
                    }
                }

                val fishSpot: Npc? = NpcQuery.newQuery().name(spotName).option(DecisionTree.actionToFish).results().nearest()
                if (fishSpot != null && fishSpot.interact(DecisionTree.actionToFish)) {
                    Execution.delay(Navi.random.nextLong(10000, 24000))
                } else {
                    Zezimax.Logger.log("No $spotName found with ${DecisionTree.actionToFish} action or failed to interact.")
                    Execution.delay(Navi.random.nextLong(1500, 3000))
                }
            }

            // Check if inventory is full during fishing process
            if (Backpack.isFull()) {
                Execution.delay(Navi.random.nextLong(500, 1200))
                Zezimax.Logger.log("Inventory is full, Navigating to Bank...")
                bank()
                return
            }
        }
    }



    fun bank() {
        Execution.delay(Navi.random.nextLong(1000, 2000))
        navigateToBankLocation()

        if (!Bank.isOpen()) {
            // Open the bank
            Bank.open()
            Execution.delay(Navi.random.nextLong(1500, 3000))
        }

        if (Bank.isOpen()) {
            if (!Backpack.isEmpty()) {
                Zezimax.Logger.log("Depositing all...")
                Execution.delay(Navi.random.nextLong(1000, 3000)) // Simulate deposit delay
                Bank.depositAll()
                Execution.delay(Navi.random.nextLong(2000, 4000)) // Simulate deposit delay
            }
            Execution.delay(Navi.random.nextLong(1000, 2000)) // Simulate deposit delay

            // Withdraw feathers if needed

            if (DecisionTree.feathersNeeded) {
                if (Utilities.isItemIdInBank(featherId)) {
                    Bank.withdrawAll(featherId)
                    Zezimax.Logger.log("Feathers withdrawn...")
                    Execution.delay(Navi.random.nextLong(1300, 2000))
                }
                else {
                    Zezimax.Logger.log("Out of feathers, Reinitializing...")
                    Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
                    return
                }
            }

            // Withdraw bait if needed

            if (DecisionTree.baitNeeded) {
                if (Utilities.isItemIdInBank(baitId)) {
                    Bank.withdrawAll(baitId)
                    Zezimax.Logger.log("Bait withdrawn...")
                    Execution.delay(Navi.random.nextLong(1300, 2000))
                }
                else {
                    Zezimax.Logger.log("Out of bait, Reinitializing...")
                    Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
                    return
                }
            }
            val fishCount = Bank.getItems().filter { it.id == fishName }.sumOf { it.stackSize }
            Zezimax.Logger.log("$fishingGetName count in bank: $fishCount")

            Bank.close()
            Execution.delay(Navi.random.nextLong(1000, 2500)) // Simulate bank closing delay

            if (fishCount >= fishUntil) {
                Zezimax.Logger.log("Collected $fishUntil or more $fishingGetName. Re-Initializing...")
                Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING
                return
            } else {
                Zezimax.Logger.log("Haven't collected $fishUntil or more $fishingGetName, Continuing to fish more $fishingGetName.")
                Zezimax.botState = Zezimax.ZezimaxBotState.START_FISHING
                return
            }
        }
        else {
            Zezimax.Logger.log("Bank is not open, retrying.")
            Execution.delay(Navi.random.nextLong(1500, 3000))
        }
    }
}