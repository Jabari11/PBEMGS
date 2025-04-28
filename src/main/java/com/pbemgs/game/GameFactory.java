package com.pbemgs.game;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.pbemgs.game.ataxx.Ataxx;
import com.pbemgs.game.gomoku.GoMoku;
import com.pbemgs.game.ironclad.Ironclad;
import com.pbemgs.game.loa.LinesOfAction;
import com.pbemgs.game.ninetac.Ninetac;
import com.pbemgs.game.surge.Surge;
import com.pbemgs.game.tac.Tac;
import com.pbemgs.game.triad.TriadCubed;
import com.pbemgs.model.GameType;
import org.jooq.DSLContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory class to create the game object of the correct type.
 */
public class GameFactory {
    public static GameInterface createGame(GameType gameType, DSLContext dslContext, LambdaLogger logger) {
        return switch (gameType) {
            case TAC -> new Tac(dslContext, logger);
            case NINETAC -> new Ninetac(dslContext, logger);
            case ATAXX -> new Ataxx(dslContext, logger);
            case SURGE -> new Surge(dslContext, logger);
            case LOA -> new LinesOfAction(dslContext, logger);
            case GOMOKU -> new GoMoku(dslContext, logger);
            case TRIAD -> new TriadCubed(dslContext, logger);
            case IRONCLAD -> new Ironclad(dslContext, logger);
            default ->
                    throw new IllegalArgumentException("GameFactory::createGame got illegal game type " + gameType.name());
        };
    }

    public static List<GameInterface> createAllGames(DSLContext dslContext, LambdaLogger logger) {
        List<GameInterface> gameList = new ArrayList<>();
        gameList.add(new Tac(dslContext, logger));
        gameList.add(new Ninetac(dslContext, logger));
        gameList.add(new Ataxx(dslContext, logger));
        gameList.add(new Surge(dslContext, logger));
        gameList.add(new LinesOfAction(dslContext, logger));
        gameList.add(new GoMoku(dslContext, logger));
        gameList.add(new TriadCubed(dslContext, logger));
        gameList.add(new Ironclad(dslContext, logger));
        return gameList;
    }
}
