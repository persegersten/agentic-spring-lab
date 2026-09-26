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
    private static String encodePlayback(List<RoundEvent> events) { return events.stream().map(e -> String.join(",",
            Integer.toString(e.sequence()),e.type().name(),e.playerId().toString(),e.vehicleId().toString(),
            Integer.toString(e.oldPosition().x()),Integer.toString(e.oldPosition().y()),
            Integer.toString(e.newPosition().x()),Integer.toString(e.newPosition().y()),
            e.oldDirection().name(),e.newDirection().name(),e.sourcePlayerId().toString(),
            e.sourceVehicleId().toString(),Integer.toString(e.oldDamage()),Integer.toString(e.newDamage())))
            .collect(java.util.stream.Collectors.joining("#")); }
    private static List<RoundEvent> decodePlayback(String value, Map<UUID,Vehicle> vehicles) {
        if(value.isBlank()) return List.of(); var result=new ArrayList<RoundEvent>();
        for(String row:value.split("#")){String[] p=row.split(","); UUID playerId=UUID.fromString(p[2]); UUID vehicleId=UUID.fromString(p[3]);
            UUID sourcePlayerId=p.length>10?UUID.fromString(p[10]):playerId; UUID sourceVehicleId=p.length>11?UUID.fromString(p[11]):vehicleId;
            int oldDamage=p.length>12?Integer.parseInt(p[12]):0; int newDamage=p.length>13?Integer.parseInt(p[13]):oldDamage;
            result.add(new RoundEvent(Integer.parseInt(p[0]),RoundEventType.valueOf(p[1]),playerId,vehicleId,sourcePlayerId,sourceVehicleId,
                    new Position(Integer.parseInt(p[4]),Integer.parseInt(p[5])),new Position(Integer.parseInt(p[6]),Integer.parseInt(p[7])),
                    Direction.valueOf(p[8]),Direction.valueOf(p[9]),oldDamage,newDamage));} return result; }
    private static String encodeStates(List<VehicleState> states) { return states.stream().map(s->s.vehicle().id()+","+s.vehicle().playerId()+","+s.position().x()+","+s.position().y()+","+s.orientation()+","+s.damage()).collect(java.util.stream.Collectors.joining("|")); }
    private static List<VehicleState> decodeStates(String value, Map<UUID,Vehicle> vehicles) { if(value.isBlank()) return List.of(); var result=new ArrayList<VehicleState>(); for(String row:value.split("\\|")){String[] p=row.split(","); UUID vehicleId=UUID.fromString(p[0]); Vehicle vehicle=vehicles.computeIfAbsent(vehicleId,id->new Vehicle(id,UUID.fromString(p[1]))); result.add(new VehicleState(vehicle,new Position(Integer.parseInt(p[2]),Integer.parseInt(p[3])),Direction.valueOf(p[4]),p.length>5?Integer.parseInt(p[5]):0));} return result; }
    private static String csv(List<MovementOrder> value) { return value.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")); }
    private static List<MovementOrder> orders(String value) { return value.isBlank()?List.of():Arrays.stream(value.split(",")).map(card -> MovementOrder.valueOf(card.equals("MALFUNCTION_REVERSE") ? "MALFUNCTION_NO_OP" : card)).toList(); }
}
