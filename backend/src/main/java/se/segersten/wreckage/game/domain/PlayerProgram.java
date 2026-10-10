package se.segersten.wreckage.game.domain;
import java.util.*;
public record PlayerProgram(UUID playerId,int programSize,List<MovementOrder> hand,List<MovementOrder> commands,boolean locked,boolean shieldSelected){
 public static final int HAND_SIZE=8;
 public PlayerProgram(UUID playerId,int programSize,List<MovementOrder> commands,boolean locked){this(playerId,programSize,List.of(),commands,locked,false);}
 public PlayerProgram{Objects.requireNonNull(playerId);if(programSize<1)throw new IllegalArgumentException("programSize must be positive");hand=hand==null?List.of():List.copyOf(hand);commands=commands==null?List.of():List.copyOf(commands);if(hand.stream().anyMatch(Objects::isNull)||commands.stream().anyMatch(Objects::isNull)||commands.size()>programSize||locked&&commands.size()!=programSize)throw new IllegalArgumentException("Program must contain exactly "+programSize+" commands when locked");if(!hand.isEmpty()&&!containsAll(hand,commands))throw new IllegalArgumentException("Program cards must belong to the dealt hand");}
 public static PlayerProgram empty(UUID id,int size){return new PlayerProgram(id,size,List.of(),List.of(),false,false);}
 public static PlayerProgram dealt(UUID id,int size,List<MovementOrder> hand){if(hand==null||hand.size()!=HAND_SIZE||hand.stream().anyMatch(card->!card.isProgrammingCard()))throw new IllegalArgumentException("A dealt hand must contain exactly 8 programming cards");return new PlayerProgram(id,size,hand,List.of(),false,false);}
 public boolean ready(){return locked;}
 public PlayerProgram edit(List<MovementOrder> selected){return edit(selected,shieldSelected);}
 public PlayerProgram edit(List<MovementOrder> selected,boolean shield){if(locked)throw new IllegalStateException("Program is already locked");return new PlayerProgram(playerId,programSize,hand,selected,false,shield);}
 public PlayerProgram lock(List<MovementOrder> selected){return lock(selected,shieldSelected);}
 public PlayerProgram lock(List<MovementOrder> selected,boolean shield){if(locked)throw new IllegalStateException("Program is already locked");return new PlayerProgram(playerId,programSize,hand,selected,true,shield);}
 public PlayerProgram completeFromHand(){if(locked)return this;var result=new ArrayList<>(commands);if(hand.isEmpty()){while(result.size()<programSize)result.add(MovementOrder.WAIT);}else{var available=new ArrayList<>(hand);for(var card:commands)available.remove(card);for(var card:available){if(result.size()==programSize)break;result.add(card);}}return new PlayerProgram(playerId,programSize,hand,result,true,shieldSelected);}
 public PlayerProgram completeWithWait(){return completeFromHand();}
 private static boolean containsAll(List<MovementOrder> hand,List<MovementOrder> selected){var remaining=new ArrayList<>(hand);for(var card:selected)if(!remaining.remove(card))return false;return true;}
}
