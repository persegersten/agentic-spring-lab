package se.segersten.wreckage.game.infrastructure;

import java.util.*;
import jakarta.persistence.*;
import se.segersten.wreckage.game.domain.*;

@Entity @Table(name="game_round")
class RoundEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="game_id", nullable=false, unique=true) private GameEntity game;
    @Column(name="round_number", nullable=false) private int number;
    @Enumerated(EnumType.STRING) @Column(name="phase", nullable=false) private RoundPhase phase;
    @Column(name="program_payload", nullable=false, length=20000) private String programPayload;
    @Column(name="initial_state_payload", nullable=false, length=20000) private String initialStatePayload;
    @Column(name="playback_payload", nullable=false, length=30000) private String playbackPayload;
    protected RoundEntity() {}
    static RoundEntity fromDomain(Round round, GameEntity game) { var e=new RoundEntity(); e.game=game; return e.updateFrom(round); }
    RoundEntity updateFrom(Round round) { number=round.number(); phase=round.phase(); programPayload=encodePrograms(round.programs()); initialStatePayload=encodeStates(round.initialState().vehicleStates()); playbackPayload=encodePlayback(round.playback()); return this; }
    Round toDomain(Board board, Map<UUID, Vehicle> vehicles) {
        GameState initial = new GameState(board, decodeStates(initialStatePayload, vehicles));
        return new Round(number, phase, decodePrograms(programPayload), initial, decodePlayback(playbackPayload, vehicles));
    }
    private static String encodePrograms(Map<UUID,PlayerProgram> programs) { return programs.values().stream().map(p -> p.playerId()+":"+csv(p.hand())+":"+csv(p.orders())).collect(java.util.stream.Collectors.joining(";")); }
    private static Map<UUID,PlayerProgram> decodePrograms(String value) { var result=new LinkedHashMap<UUID,PlayerProgram>(); if(value.isBlank()) return result; for(String row:value.split(";")){String[] p=row.split(":",-1); UUID id=UUID.fromString(p[0]); result.put(id,new PlayerProgram(id,orders(p[1]),orders(p[2])));} return result; }
    private static String encodePlayback(List<PlaybackStep> steps) { return steps.stream().map(s -> s.index()+"~"+s.commands().entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(","))+"~"+encodeStates(s.vehicleStates())).collect(java.util.stream.Collectors.joining("#")); }
    private static List<PlaybackStep> decodePlayback(String value, Map<UUID,Vehicle> vehicles) { if(value.isBlank()) return List.of(); var result=new ArrayList<PlaybackStep>(); for(String row:value.split("#")){String[] p=row.split("~",-1); var commands=new LinkedHashMap<UUID,MovementOrder>(); if(!p[1].isBlank()) for(String c:p[1].split(",")){String[] pair=c.split("="); commands.put(UUID.fromString(pair[0]),MovementOrder.valueOf(pair[1]));} result.add(new PlaybackStep(Integer.parseInt(p[0]),commands,decodeStates(p[2],vehicles)));} return result; }
    private static String encodeStates(List<VehicleState> states) { return states.stream().map(s->s.vehicle().id()+","+s.vehicle().playerId()+","+s.position().x()+","+s.position().y()+","+s.orientation()).collect(java.util.stream.Collectors.joining("|")); }
    private static List<VehicleState> decodeStates(String value, Map<UUID,Vehicle> vehicles) { if(value.isBlank()) return List.of(); var result=new ArrayList<VehicleState>(); for(String row:value.split("\\|")){String[] p=row.split(","); UUID vehicleId=UUID.fromString(p[0]); Vehicle vehicle=vehicles.computeIfAbsent(vehicleId,id->new Vehicle(id,UUID.fromString(p[1]))); result.add(new VehicleState(vehicle,new Position(Integer.parseInt(p[2]),Integer.parseInt(p[3])),Direction.valueOf(p[4])));} return result; }
    private static String csv(List<MovementOrder> value) { return value.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")); }
    private static List<MovementOrder> orders(String value) { return value.isBlank()?List.of():Arrays.stream(value.split(",")).map(MovementOrder::valueOf).toList(); }
}
