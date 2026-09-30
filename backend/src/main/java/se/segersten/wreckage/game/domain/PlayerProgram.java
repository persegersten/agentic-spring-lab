package se.segersten.wreckage.game.domain;
import java.util.*;
public record PlayerProgram(UUID playerId,int programSize,List<MovementOrder> commands,boolean locked){
 public PlayerProgram{Objects.requireNonNull(playerId);if(programSize<1)throw new IllegalArgumentException("programSize must be positive");commands=commands==null?List.of():List.copyOf(commands);if(commands.stream().anyMatch(Objects::isNull)||commands.size()>programSize||locked&&commands.size()!=programSize)throw new IllegalArgumentException("Program must contain exactly "+programSize+" commands when locked");}
 public static PlayerProgram empty(UUID id,int size){return new PlayerProgram(id,size,List.of(),false);} public boolean ready(){return locked;}
 public PlayerProgram edit(List<MovementOrder> selected){if(locked)throw new IllegalStateException("Program is already locked");return new PlayerProgram(playerId,programSize,selected,false);}
 public PlayerProgram lock(List<MovementOrder> selected){if(locked)throw new IllegalStateException("Program is already locked");return new PlayerProgram(playerId,programSize,selected,true);}
 public PlayerProgram completeWithWait(){if(locked)return this;var result=new ArrayList<>(commands);while(result.size()<programSize)result.add(MovementOrder.WAIT);return new PlayerProgram(playerId,programSize,result,true);}
}
