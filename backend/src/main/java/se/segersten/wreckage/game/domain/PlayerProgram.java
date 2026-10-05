package se.segersten.wreckage.game.domain;
import java.util.*;
public record PlayerProgram(UUID playerId,int programSize,List<MovementOrder> commands,boolean locked,ScheduledAction scheduledAction){
 public PlayerProgram(UUID playerId,int programSize,List<MovementOrder> commands,boolean locked){this(playerId,programSize,commands,locked,null);}
 public PlayerProgram{Objects.requireNonNull(playerId);if(programSize<1)throw new IllegalArgumentException("programSize must be positive");commands=commands==null?List.of():List.copyOf(commands);if(commands.stream().anyMatch(Objects::isNull)||commands.size()>programSize||locked&&commands.size()!=programSize)throw new IllegalArgumentException("Program must contain exactly "+programSize+" commands when locked");if(scheduledAction!=null&&scheduledAction.registerIndex()>programSize)throw new IllegalArgumentException("Scheduled action register must be between 1 and "+programSize);}
 public static PlayerProgram empty(UUID id,int size){return new PlayerProgram(id,size,List.of(),false,null);} public boolean ready(){return locked;}
 public PlayerProgram edit(List<MovementOrder> selected){return edit(selected,scheduledAction);}
 public PlayerProgram edit(List<MovementOrder> selected,ScheduledAction action){if(locked)throw new IllegalStateException("Program is already locked");return new PlayerProgram(playerId,programSize,selected,false,action);}
 public PlayerProgram schedule(ScheduledAction action){return edit(commands,action);}
 public PlayerProgram lock(List<MovementOrder> selected){return lock(selected,scheduledAction);}
 public PlayerProgram lock(List<MovementOrder> selected,ScheduledAction action){if(locked)throw new IllegalStateException("Program is already locked");return new PlayerProgram(playerId,programSize,selected,true,action);}
 public PlayerProgram completeWithWait(){if(locked)return this;var result=new ArrayList<>(commands);while(result.size()<programSize)result.add(MovementOrder.WAIT);return new PlayerProgram(playerId,programSize,result,true,scheduledAction);}
}
