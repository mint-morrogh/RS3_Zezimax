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
import net.botwithus.rs3.script.config.ScriptConfig
import net.botwithus.rs3.util.Regex
import java.util.*
import java.util.concurrent.Callable
import java.util.regex.Pattern

class Zezimax(
    name: String,
    scriptConfig: ScriptConfig,
    scriptDefinition: ScriptDefinition
) : LoopingScript(name, scriptConfig, scriptDefinition) {

    enum class ZezimaxBotState {
        INITIALIZING,
        IDLE,
        START_MINING,
        MINING,
        NAVIGATING_TO_MINE,
        NAVIGATING_TO_BANK,
        MINING_BANKING,
        START_SMITHING_ORE,
        SMITHING_ORE
    }

    companion object {
        var botState: ZezimaxBotState = ZezimaxBotState.INITIALIZING
    }

    var someBoolean: Boolean = true

    override fun initialize(): Boolean {
        super.initialize()
        println("Script initialized. State set to INITIALIZING.")
        return true
    }

    // MAIN BOT LOOP //
    override fun onLoop() {
        val player = Client.getLocalPlayer()
        if (Client.getGameState() != Client.GameState.LOGGED_IN || player == null) {
            println("Player not logged in. Delaying execution.")
            Execution.delay(Navi.random.nextLong(2500, 7500))
            return
        }

        when (botState) {
            ZezimaxBotState.INITIALIZING -> {
                if (initializeBanking()) {
                    botState = ZezimaxBotState.IDLE
                }
            }

            ZezimaxBotState.IDLE -> {
                println("Bot state is IDLE. Decision: ${DecisionTree.decision}")
                if (DecisionTree.decision == null) {
                    DecisionTree.makeRandomDecision()
                } else {
                    when (DecisionTree.decision) {
                        0 -> botState = ZezimaxBotState.START_MINING
                        1 -> botState = ZezimaxBotState.START_SMITHING_ORE
                    }
                }
            }


            // MINING STATES
            ZezimaxBotState.START_MINING -> {
                println("Decided to Mine...")
                botState = ZezimaxBotState.NAVIGATING_TO_MINE
            }
            ZezimaxBotState.NAVIGATING_TO_MINE -> {
                if (Navi.walkToMiningGuild()) {
                    println("Reached Rune.")
                    println("Mining...")
                    botState = ZezimaxBotState.MINING
                } else {
                    Execution.delay(Navi.random.nextLong(1000, 3000))
                }
            }
            ZezimaxBotState.NAVIGATING_TO_BANK -> {
                if (Navi.walkToFaladorSmithBank()) {
                    println("Reached Falador Smith Bank.")
                    botState = ZezimaxBotState.MINING_BANKING
                } else {
                    println("Walking to Falador smith bank.")
                    Execution.delay(Navi.random.nextLong(1000, 3000))
                }
            }
            ZezimaxBotState.MINING -> {
                Mining("Runite rock", "Runite ore", "Rune ore box").mine(player)
            }
            ZezimaxBotState.MINING_BANKING -> {
                MiningBanking("Runite ore", "Rune ore box", 150).bank()
            }


            // SMITHING ORE STATES
            ZezimaxBotState.START_SMITHING_ORE -> {
                println("Decided to Smith Ore...")
                depositOreInFurnace()
                Execution.delay(Navi.random.nextLong(500, 2000))
                botState = ZezimaxBotState.SMITHING_ORE
            }

            ZezimaxBotState.SMITHING_ORE -> {
                smithOre()
            }
        }
    }
    private fun initializeBanking(): Boolean {
        val player = Client.getLocalPlayer() ?: return false

        val nearestBank = Navi.getNearestBank(player.coordinate) ?: return false

        if (!nearestBank.contains(player.coordinate)) {
            println("**INITIALIZING** Walking to the nearest Bank...")
            val path = NavPath.resolve(nearestBank.randomWalkableCoordinate)
            if (path != null && Movement.traverse(path) != TraverseEvent.State.NO_PATH) {
                Execution.delay(Navi.random.nextLong(700, 1500))
                return false
            }
        }

        if (!Bank.isOpen()) {
            // Open the bank
            val bankBooth: SceneObject? =
                SceneObjectQuery.newQuery().name("Bank booth", "Bank chest", "Counter").results().nearest()
            if (bankBooth != null && (bankBooth.interact("Bank") || bankBooth.interact("Use"))) {
                println("**INITIALIZING** Pre Decision Tree Banking...")
                Execution.delayUntil(5000, Callable { Bank.isOpen() })
            } else {
                println("No bank booth or chest found or failed to interact.")
                Execution.delay(Navi.random.nextLong(1500, 3000))
                return false
            }
        }

        // Deposit all items into the bank
        if (Bank.isOpen()) {
            println("**INITIALIZING** Depositing all items.")
            Execution.delay(Navi.random.nextLong(1000, 3000)) // Simulate deposit delay
            Bank.depositAll()
            Execution.delay(Navi.random.nextLong(2000, 4000)) // Simulate deposit delay

            // Make random decision on what to do next
            DecisionTree.makeRandomDecision()

            // Close the bank
            Bank.close()
            Execution.delay(Navi.random.nextLong(1000, 3000)) // Simulate bank closing delay
            println("Initialization complete. Starting main script.")
            return true
        } else {
            println("Bank is not open, retrying.")
            Execution.delay(Navi.random.nextLong(1500, 3000))
            return false
        }
    }

    fun smithOre() {
        while (true) {
            // Select bar in interface - component 37, 103, subcomponent 11 for "Rune bar"
            val runeBarComponent = ComponentQuery.newQuery(37)
                .componentIndex(103)
                .subComponentIndex(11)
                .results()
                .firstOrNull()

            if (runeBarComponent != null) {
                Execution.delay(Navi.random.nextLong(400, 1000))
                runeBarComponent.interact("Select Rune bar")
                println("Selected Rune bar.")
                Execution.delay(Navi.random.nextLong(400, 1000))
            } else {
                println("Rune bar component not found.")
            }

            // Press begin project button - component 37, 163
            val beginProjectButton = ComponentQuery.newQuery(37)
                .componentIndex(163)
                .results()
                .firstOrNull()

            if (beginProjectButton != null) {
                Execution.delay(Navi.random.nextLong(400, 1000))
                beginProjectButton.interact()
                println("Pressed Begin Project button.")
                Execution.delay(Navi.random.nextLong(3000, 6000))
            } else {
                println("Begin Project button not found.")
            }

            /*
            // Check smithing interface, if still open, deposit materials and break out of this loop
            val smithingInterface = ComponentQuery.newQuery(858) // 838 doesnt work
                .results()
                .firstOrNull()

            if (smithingInterface != null) {
             */

            if (Interfaces.isOpen(37)) {
                // Click deposit materials
                val smithDepositAllButton = ComponentQuery.newQuery(37)
                    .componentIndex(167) // Assuming component index for "Deposit All"
                    .results()
                    .firstOrNull()

                if (smithDepositAllButton != null) {
                    smithDepositAllButton.interact()
                    println("Deposited all materials.")
                    Execution.delay(Navi.random.nextLong(1500, 2500))
                    println("Not enough materials to continue, returning to Decision Tree.")
                    Zezimax.botState = Zezimax.ZezimaxBotState.INITIALIZING // back to decision tree
                    return
                } else {
                    println("Deposit All button not found.")
                }

            } else {
                // WAIT FOR EXP INTERFACE TO GO AWAY
                // Check exp interface
                while (!ComponentQuery.newQuery(1251).results().isEmpty) {
                    Execution.delay(Navi.random.nextLong(2000, 6000))
                }
            }

            // Interact with furnace again to return bars
            val furnace = SceneObjectQuery.newQuery()
                .name("Furnace")
                .results()
                .nearest()

            if (furnace != null) {
                furnace.interact("Smelt")
                println("Interacted with Furnace to Smelt.")
                Execution.delay(Navi.random.nextLong(1200, 1900))
            } else {
                println("Furnace not found.")
                Execution.delay(Navi.random.nextLong(1200, 1900))
            }

            // Click deposit materials again
            val smithDepositAllButton2 = ComponentQuery.newQuery(37)
                .componentIndex(167) // Assuming component index for "Deposit All"
                .results()
                .firstOrNull()

            if (smithDepositAllButton2 != null) {
                Execution.delay(Navi.random.nextLong(1500, 2500))
                smithDepositAllButton2.interact()
                println("Deposited all materials.")
            } else {
                println("Deposit All button not found.")
            }
        }
    }
// End of loop
}



