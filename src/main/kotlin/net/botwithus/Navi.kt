package net.botwithus

import net.botwithus.rs3.game.Coordinate
import net.botwithus.rs3.game.Area
import net.botwithus.rs3.game.movement.Movement
import net.botwithus.rs3.game.movement.NavPath
import net.botwithus.rs3.game.movement.TraverseEvent
import net.botwithus.rs3.script.Execution
import net.botwithus.rs3.game.Client
import java.util.*

// Define Bot States for navigation
enum class BotState {
    STANDING,
    WALKING
}

object Navi {
    val random: Random = Random()
    var botState: BotState = BotState.STANDING

    // Define navigation areas here
    //////////////////////////////////
    val faladorCenter = Area.Rectangular(
        Coordinate(2961, 3376, 0), // Bottom-left corner
        Coordinate(2971, 3386, 0)  // Top-right corner
    )
    val faladorWestBank = Area.Rectangular(
        Coordinate(2945, 3368, 0),
        Coordinate(2949, 3368, 0)
    )
    val faladorEastBank = Area.Rectangular(
        Coordinate(3010, 3355, 0),
        Coordinate(3015, 3355, 0)
    )
    val faladorSouthBank = Area.Rectangular(
        Coordinate(2956, 3296, 0),
        Coordinate(2956, 3297, 0)
    )
    val faladorSouthFiremaking = Area.Rectangular(
        Coordinate(2959, 3292, 0),
        Coordinate(2963, 3298, 0)
    )
    val faladorSmithBank = Area.Rectangular(
        Coordinate(3060, 3339, 0),
        Coordinate(3060, 3340, 0)
    )
    val grandexchange = Area.Rectangular(
        Coordinate(3163, 3484, 0),
        Coordinate(3166, 3484, 0)
    )
    val grandexchangeEntrance = Area.Rectangular(
        Coordinate(3163, 3452, 0),
        Coordinate(3169, 3458, 0)
    )
    val varrockWestBank = Area.Rectangular(
        Coordinate(3189, 3435, 0),
        Coordinate(3189, 3443, 0)
    )
    val varrockEastBank = Area.Rectangular(
        Coordinate(3251, 3420, 0),
        Coordinate(3256, 3420, 0)
    )
    val varrockEastMine = Area.Rectangular(
        Coordinate(3287, 3364, 0),
        Coordinate(3290, 3367, 0)
    )
    val varrockWestMine = Area.Rectangular(
        Coordinate(3181, 3371, 0),
        Coordinate(3184, 3375, 0)
    )
    val varrockWestTrees = Area.Rectangular(
        Coordinate(3134, 3420, 0),
        Coordinate(3147, 3443, 0)
    )
    val varrockEastYews = Area.Rectangular(
        Coordinate(3264, 3484, 0),
        Coordinate(3269, 3493, 0)
    )
    val varrockEastYewsBank = Area.Rectangular(
        Coordinate(3254, 3499, 0),
        Coordinate(3255, 3500, 0)
    )
    val lumbridgeTopFloorBank = Area.Rectangular(
        Coordinate(3208, 3219, 2),
        Coordinate(3208, 3220, 2)
    )
    val alkharidWestBank = Area.Rectangular(
        Coordinate(3268, 3168, 0),
        Coordinate(3272, 3168, 0)
    )
    val alkharidWestFishing = Area.Rectangular(
        Coordinate(3260, 3175, 0),
        Coordinate(3257, 3178, 0)
    )
    val alkharidWestRange = Area.Rectangular(
        Coordinate(3269, 3182, 0),
        Coordinate(3269, 3184, 0)
    )
    val draynorBank = Area.Rectangular(
        Coordinate(3092, 3241, 0),
        Coordinate(3092, 3245, 0)
    )
    val draynorOaks = Area.Rectangular(
        Coordinate(3074, 3288, 0),
        Coordinate(3102, 3303, 0)
    )
    val draynorWillows = Area.Rectangular(
        Coordinate(3079, 3240, 0),
        Coordinate(3096, 3226, 0)
    )
    val edgevilleBank = Area.Rectangular(
        Coordinate(3094, 3489, 0),
        Coordinate(3094, 3493, 0)
    )
    val edgevilleFiremaking = Area.Rectangular(
        Coordinate(3085, 3494, 0),
        Coordinate(3087, 3498, 0)
    )
    val burthorpeBank = Area.Rectangular(
        Coordinate(2888, 3535, 0),
        Coordinate(2887, 3536, 0)
    )
    val taverlyBank = Area.Rectangular(
        Coordinate(2875, 3416, 0),
        Coordinate(2875, 3418, 0)
    )
    val faladorMiningGuild = Area.Rectangular(
        Coordinate(3022, 9737, 0),
        Coordinate(3023, 9740, 0)
    )
    val faladorLuminite = Area.Rectangular(
        Coordinate(3038, 9761, 0),
        Coordinate(3040, 9764, 0)
    )
    val faladorSmithingFurnace = Area.Rectangular(
        Coordinate(3042, 3337, 0),
        Coordinate(3047, 3338, 0)
    )
    val portSarimFIshingShop = Area.Rectangular(
        Coordinate(3015, 3222, 0),
        Coordinate(3013, 3224, 0)
    )
    val barbarianVillageFishing = Area.Rectangular(
        Coordinate(3107, 3434, 0),
        Coordinate(3105, 3431, 0)
    )
    val portSarimPayFare = Area.Rectangular(
        Coordinate(3025, 3215, 0),
        Coordinate(3029, 3220, 0)
    )
    val portSarimFishing = Area.Rectangular(
        Coordinate(2924, 3180, 0),
        Coordinate(2925, 3175, 0)
    )
    //////////////////////////////////

    // Finding closest bank
    private val bankAreas = listOf(
        faladorWestBank,
        faladorEastBank,
        faladorSouthBank,
        faladorSmithBank,
        // grandexchange, to avoid getting stuck in agility, only if members should this be valid
        varrockWestBank,
        varrockEastBank,
        lumbridgeTopFloorBank,
        alkharidWestBank,
        draynorBank,
        edgevilleBank,
        burthorpeBank,
        taverlyBank,
        varrockEastYewsBank
    )

    fun getNearestBank(playerCoord: Coordinate): Area.Rectangular? {
        return bankAreas.minByOrNull { it.center().distanceTo(playerCoord) }
    }

    private fun Area.Rectangular.center(): Coordinate {
        val centerX = (topLeft.x + bottomRight.x) / 2
        val centerY = (topLeft.y + bottomRight.y) / 2
        return Coordinate(centerX, centerY, topLeft.z)
    }

    private fun Coordinate.distanceTo(other: Coordinate): Double {
        return Math.sqrt(Math.pow((this.x - other.x).toDouble(), 2.0) + Math.pow((this.y - other.y).toDouble(), 2.0))
    }




    // Define a utility function for walking to an area
    fun walkTo(area: Area.Rectangular): Boolean {
        val player = Client.getLocalPlayer() ?: return false
        if (area.contains(player.coordinate)) {
            botState = BotState.STANDING
            return true
        }

        if (player.animationId != -1) {
            // Walk to current player coordinates to break the animation
            val currentCoord = player.coordinate
            Zezimax.Logger.log("**TRAVELLING** Player is animating. Breaking animation...")

            // Walk to the current tile without using the minimap
            Movement.walkTo(currentCoord.x, currentCoord.y, false)
            Execution.delay(random.nextLong(1000, 2000))
        }

        val targetCoord = area.randomWalkableCoordinate
        val path = NavPath.resolve(targetCoord)

        if (path == null) {
            Zezimax.Logger.log("Failed to find path to target area.")
            return false
        }

        val results = Movement.traverse(path)
        if (results == TraverseEvent.State.NO_PATH) {
            Zezimax.Logger.log("Failed to traverse path to target area.")
            return false
        }

        val delay = random.nextLong(300, 1200) // Random delay
        Zezimax.Logger.log("**TRAVELLING** Delaying next input for $delay milliseconds")
        Execution.delay(delay)

        return area.contains(player.coordinate)
    }




    // Wrapper functions for walking to specific areas
    fun walkToFaladorCenter(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorCenter)
    }

    fun walkToFaladorWestBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorWestBank)
    }

    fun walkToFaladorEastBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorEastBank)
    }

    fun walkToFaladorSouthBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorSouthBank)
    }

    fun walkToFaladorSouthFiremaking(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorSouthFiremaking)
    }

    fun walkToVarrockWestBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(varrockWestBank)
    }

    fun walkToVarrockEastBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(varrockEastBank)
    }

    fun walkToVarrockEastMine(): Boolean {
        botState = BotState.WALKING
        return walkTo(varrockEastMine)
    }

    fun walkToVarrockWestMine(): Boolean {
        botState = BotState.WALKING
        return walkTo(varrockWestMine)
    }

    fun walkToVarrockWestTrees(): Boolean {
        botState = BotState.WALKING
        return walkTo(varrockWestTrees)
    }

    fun walkToVarrockEastYews(): Boolean {
        botState = BotState.WALKING
        return walkTo(varrockEastYews)
    }

    fun walkToVarrockEastYewsBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(varrockEastYewsBank)
    }

    fun walkToDraynorBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(draynorBank)
    }

    fun walkToDraynorOaks(): Boolean {
        botState = BotState.WALKING
        return walkTo(draynorOaks)
    }

    fun walkToDraynorWillows(): Boolean {
        botState = BotState.WALKING
        return walkTo(draynorWillows)
    }

    fun walkToEdgevilleBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(edgevilleBank)
    }

    fun walkToEdgevilleFiremaking(): Boolean {
        botState = BotState.WALKING
        return walkTo(edgevilleFiremaking)
    }

    fun walkToBurthorpeBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(burthorpeBank)
    }

    fun walkToTaverlyBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(taverlyBank)
    }

    fun walkToAlkharidWestBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(alkharidWestBank)
    }

    fun walkToAlkharidWestFishing(): Boolean {
        botState = BotState.WALKING
        return walkTo(alkharidWestFishing)
    }

    fun walkToAlkharidWestRange(): Boolean {
        botState = BotState.WALKING
        return walkTo(alkharidWestRange)
    }

    fun walkToLumbridgeTopFloorBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(lumbridgeTopFloorBank)
    }

    fun walkToGrandExchange(): Boolean {
        botState = BotState.WALKING
        return walkTo(grandexchange)
    }

    fun walkToGrandExchangeEntrance(): Boolean {
        botState = BotState.WALKING
        return walkTo(grandexchangeEntrance)
    }

    fun walkToMiningGuild(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorMiningGuild)
    }

    fun walkToFaladorSmithBank(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorSmithBank)
    }
    fun walkToFaladorLuminite(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorLuminite)
    }
    fun walkToFaladorSmithingFurnace(): Boolean {
        botState = BotState.WALKING
        return walkTo(faladorSmithingFurnace)
    }
    fun walkToPortSarimFishingShop(): Boolean {
        botState = BotState.WALKING
        return walkTo(portSarimFIshingShop)
    }
    fun walkToBarbarianVillageFishing(): Boolean {
        botState = BotState.WALKING
        return walkTo(barbarianVillageFishing)
    }
    fun walkToPortSarimPayFare(): Boolean {
        botState = BotState.WALKING
        return walkTo(portSarimPayFare)
    }
    fun walkToPortSarimFishing(): Boolean {
        botState = BotState.WALKING
        return walkTo(portSarimFishing)
    }
}