/**
 * Pokemon Go Bot  Copyright (C) 2016  PokemonGoBot-authors (see authors.md for more information)
 * This program comes with ABSOLUTELY NO WARRANTY;
 * This is free software, and you are welcome to redistribute it under certain conditions.
 *
 * For more information, refer to the LICENSE file in this repositories root directory
 */

package ink.abb.pogo.scraper.tasks

import POGOProtos.Networking.Responses.ReleasePokemonResponseOuterClass.ReleasePokemonResponse.Result
import com.pokegoapi.api.pokemon.Pokemon
import ink.abb.pogo.scraper.Bot
import ink.abb.pogo.scraper.Context
import ink.abb.pogo.scraper.Settings
import ink.abb.pogo.scraper.Task
import ink.abb.pogo.scraper.util.Log
import ink.abb.pogo.scraper.util.pokemon.getIv
import ink.abb.pogo.scraper.util.pokemon.getIvPercentage
import ink.abb.pogo.scraper.util.pokemon.shouldTransfer

class ReleasePokemon : Task {
    override fun run(bot: Bot, ctx: Context, settings: Settings) {
        val groupedPokemon = ctx.api.inventories.pokebank.pokemons.groupBy { it.pokemonId }
        val sortByIV = settings.sortByIV
        val pokemonCounts = hashMapOf<String, Int>()

        groupedPokemon.forEach {
            var sorted: List<Pokemon>
            if (sortByIV) {
                sorted = it.value.sortedByDescending { it.getIv() }
            } else {
                sorted = it.value.sortedByDescending { it.cp }
            }
            for ((index, pokemon) in sorted.withIndex()) {
                // don't drop favorited or nicknamed pokemon
                val isFavourite = pokemon.nickname.isNotBlank() || pokemon.isFavorite
                if (!isFavourite) {
                    val ivPercentage = pokemon.getIvPercentage()
                    // never transfer highest rated Pokemon (except for obligatory transfer)
                    if (settings.obligatoryTransfer.contains(pokemon.pokemonId.name) || index >= settings.keepPokemonAmount) {
                        var (shouldRelease, reason) = pokemon.shouldTransfer(settings)

                        if (!shouldRelease) {
                            if (isTooMany(settings, pokemonCounts, pokemon)) {
                                shouldRelease = true
                                reason = "Too many"
                            }
                        }

                        if (shouldRelease) {
                            Log.yellow("Going to transfer ${pokemon.pokemonId.name} with " +
                                    "CP ${pokemon.cp} and IV $ivPercentage%; reason: $reason")
                            val result = pokemon.transferPokemon()
                            if (result == Result.SUCCESS) {
                                ctx.pokemonStats.second.andIncrement
                            } else {
                                Log.red("Failed to transfer ${pokemon.pokemonId.name}: ${result.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    fun isTooMany(settings: Settings, pokemonCounts: MutableMap<String, Int>, pokemon: Pokemon): Boolean {
        val max = settings.maxPokemonAmount
        if (max == -1) {
            return false
        }
        val name = pokemon.pokemonId.name
        if (!pokemonCounts.containsKey(name)) {
            pokemonCounts.set(name, 0)
        }
        val count = pokemonCounts.get(name)!! + 1
        pokemonCounts.set(name, count)
        return (count > max)
    }
}
