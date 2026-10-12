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
    @Column(name="initiative_payload", nullable=false, length=20000) private String initiativePayload;
    @Column(name="initial_state_payload", nullable=false, length=20000) private String initialStatePayload;
    @Column(name="playback_payload", nullable=false, length=30000) private String playbackPayload;
    @Column(name="planning_deadline",nullable=false) private java.time.Instant planningDeadline;
    @Column(name="initial_scores_payload",nullable=false,length=20000) private String initialScoresPayload;
    @Column(name="start_events_payload",nullable=false,length=30000) private String startEventsPayload;
    protected RoundEntity() {}
    static RoundEntity fromDomain(Round round, GameEntity game) { var e=new RoundEntity(); e.game=game; return e.updateFrom(round); }
    RoundEntity updateFrom(Round round) { number=round.number(); phase=round.phase(); planningDeadline=round.planningDeadline();programPayload=encodePrograms(round.programs()); initiativePayload=round.initiative().stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")); initialStatePayload=encodeStates(round.initialState().vehicleStates()); playbackPayload=encodePlayback(round.playback()); initialScoresPayload=encodeScores(round.initialScores()); startEventsPayload=encodePlayback(round.startEvents()); return this; }
    Round toDomain(Board board, Map<UUID, Vehicle> vehicles) {
        GameState initial = new GameState(board, decodeStates(initialStatePayload, vehicles));
        Map<UUID,PlayerProgram> programs = decodePrograms(programPayload);
        List<UUID> initiative = initiativePayload == null || initiativePayload.isBlank()
                ? List.copyOf(programs.keySet())
                : Arrays.stream(initiativePayload.split(",")).map(UUID::fromString).toList();
        return new Round(number, phase, programs, initiative, initial,
                decodePlayback(playbackPayload),planningDeadline,decodeScores(initialScoresPayload),
                decodePlayback(startEventsPayload));
    }
    // Keep seven legacy segments so existing delimiter payloads remain readable. Segment 5 now stores shield selection.
    private static String encodePrograms(Map<UUID,PlayerProgram> programs) { return programs.values().stream().map(p -> p.playerId()+":"+p.programSize()+":"+p.locked()+":"+csv(p.commands())+"::"+p.shieldSelected()+":"+csv(p.hand())).collect(java.util.stream.Collectors.joining(";")); }
    private static Map<UUID,PlayerProgram> decodePrograms(String value) { var result=new LinkedHashMap<UUID,PlayerProgram>(); if(value==null||value.isBlank()) return result; for(String row:value.split(";")){String[] p=row.split(":",-1); UUID id=UUID.fromString(p[0]);List<MovementOrder> commands=orders(p[3]);boolean shield=p.length>5&&("true".equalsIgnoreCase(p[5])||"SHIELD".equals(p[4]));result.put(id,p.length>6&&!p[6].isBlank()?new PlayerProgram(id,Integer.parseInt(p[1]),orders(p[6]),commands,Boolean.parseBoolean(p[2]),shield):new PlayerProgram(id,Integer.parseInt(p[1]),List.of(),commands,Boolean.parseBoolean(p[2]),shield));} return result; }
    // Preserve the old 25-column shape, leaving removed damage/ammunition slots blank.
    private static String encodePlayback(List<RoundEvent> events) { return events.stream().map(e -> String.join(",",
            Integer.toString(e.sequence()),e.type().name(),e.playerId().toString(),e.vehicleId().toString(),
            Integer.toString(e.oldPosition().x()),Integer.toString(e.oldPosition().y()),
            Integer.toString(e.newPosition().x()),Integer.toString(e.newPosition().y()),
            e.oldDirection().name(),e.newDirection().name(),e.sourcePlayerId().toString(),
            e.sourceVehicleId().toString(),"","","",nullable(e.oldScore()),nullable(e.newScore()),nullable(e.scoreDelta()),
            e.scoreReason()==null?"":e.scoreReason().name(),e.checkpointId()==null?"":e.checkpointId(),
            e.actionType()==null?"":e.actionType().name(),"","","",nullable(e.registerIndex())))
            .collect(java.util.stream.Collectors.joining("#")); }
    private static List<RoundEvent> decodePlayback(String value) {
        if(value==null||value.isBlank()) return List.of(); var result=new ArrayList<RoundEvent>();
        for(String row:value.split("#")){String[] p=row.split(",",-1); if(isRemovedCombatType(p[1])) continue;
            RoundEventType type; try { type="PIT".equals(p[1])?RoundEventType.CRASH:RoundEventType.valueOf(p[1]); } catch(IllegalArgumentException ignored){continue;}
            UUID playerId=UUID.fromString(p[2]); UUID vehicleId=UUID.fromString(p[3]);
            UUID sourcePlayerId=p.length>10&&!p[10].isBlank()?UUID.fromString(p[10]):playerId; UUID sourceVehicleId=p.length>11&&!p[11].isBlank()?UUID.fromString(p[11]):vehicleId;
            int scoreOffset=p.length>=20?15:p.length>=19?14:12; boolean hasScoreFields=p.length>=17;
            ScoreChangeReason reason=null; if(hasScoreFields&&p.length>scoreOffset+3&&!p[scoreOffset+3].isBlank()){try{reason=ScoreChangeReason.valueOf(p[scoreOffset+3]);}catch(IllegalArgumentException ignored){continue;}}
            ActionType action=null; if(p.length>20&&"LASER".equals(p[20])) action=ActionType.LASER;
            result.add(new RoundEvent(Integer.parseInt(p[0]),type,playerId,vehicleId,sourcePlayerId,sourceVehicleId,
                    new Position(Integer.parseInt(p[4]),Integer.parseInt(p[5])),new Position(Integer.parseInt(p[6]),Integer.parseInt(p[7])),
                    Direction.valueOf(p[8]),Direction.valueOf(p[9]),hasScoreFields?integer(p,scoreOffset):null,
                    hasScoreFields?integer(p,scoreOffset+1):null,hasScoreFields?integer(p,scoreOffset+2):null,reason,
                    hasScoreFields&&p.length>scoreOffset+4&&!p[scoreOffset+4].isBlank()?p[scoreOffset+4]:null,
                    action,integer(p,24)));} return List.copyOf(result); }
    private static boolean isRemovedCombatType(String type) { return Set.of("FIRE","HIT","DAMAGE","DAMAGE_APPLIED","DAMAGE_PREVENTED","AMMO_CHANGED","VEHICLE_CRASHED","ANCHOR_ACTIVATED","TURBO_ACTIVATED","SIDE_STEP").contains(type); }
    private static String encodeStates(List<VehicleState> states) { return states.stream().map(s->s.vehicle().id()+","+s.vehicle().playerId()+","+s.position().x()+","+s.position().y()+","+s.orientation()+",0,"+s.status()+",0,"+(s.shieldAvailable()?"LASER":"REPULSOR")+",SHIELD").collect(java.util.stream.Collectors.joining("|")); }
    private static List<VehicleState> decodeStates(String value, Map<UUID,Vehicle> vehicles) { if(value==null||value.isBlank()) return List.of(); var result=new ArrayList<VehicleState>(); for(String row:value.split("\\|")){String[] p=row.split(","); UUID vehicleId=UUID.fromString(p[0]); Vehicle persisted=vehicles.get(vehicleId); Vehicle vehicle=persisted==null?new Vehicle(vehicleId,UUID.fromString(p[1])):persisted; int statusIndex=p.length>=7?6:5; boolean shieldAvailable=p.length<9||!"REPULSOR".equals(p[8]); result.add(new VehicleState(vehicle,new Position(Integer.parseInt(p[2]),Integer.parseInt(p[3])),Direction.valueOf(p[4]),p.length>statusIndex?VehicleStatus.valueOf(p[statusIndex]):VehicleStatus.ACTIVE,shieldAvailable));} return result; }
    private static String csv(List<MovementOrder> value) { return value.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(",")); }
    private static List<MovementOrder> orders(String value) { return value.isBlank()?List.of():Arrays.stream(value.split(",")).map(MovementOrder::valueOf).toList(); }
    private static String encodeScores(Map<UUID,Integer> scores) { return scores.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+":"+e.getValue()).collect(java.util.stream.Collectors.joining(",")); }
    private static Map<UUID,Integer> decodeScores(String value) { var scores=new LinkedHashMap<UUID,Integer>(); if(value==null||value.isBlank())return scores; for(String row:value.split(",")){String[] p=row.split(":");scores.put(UUID.fromString(p[0]),Integer.parseInt(p[1]));}return scores; }
    private static String nullable(Integer value) { return value == null ? "" : value.toString(); }
    private static Integer integer(String[] values,int index) { return values.length>index&&!values[index].isBlank()?Integer.valueOf(values[index]):null; }
}
