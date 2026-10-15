package ai.arena.qatar.render;

import ai.arena.qatar.world.QatarPack;

/** Deterministic, bounded visual rules shared by the mobile renderer and tests. */
public final class QatarVisualStyle {
    public static final int FACADE_LIMESTONE=0;
    public static final int FACADE_SANDSTONE=1;
    public static final int FACADE_WHITE_PLASTER=2;
    public static final int FACADE_GLASS_TOWER=3;
    public static final int FACADE_INDUSTRIAL=4;
    public static final int FACADE_FARM=5;
    public static final int FACADE_CIVIC=6;
    public static final int MAX_ROAD_JOINTS_PER_CELL=384;
    public static final int ROAD_JOINT_DIVISIONS=8;
    public static final int MAX_DASHES_PER_SEGMENT=48;
    public static final int MAX_MARKING_STRIPS_PER_CELL=2400;
    public static final int MAX_ROAD_SUBDIVISIONS=16;
    public static final float ROAD_TERRAIN_STEP_METRES=32f;

    private QatarVisualStyle(){}

    public static int facadePalette(QatarPack.Building building){
        return switch(building.archetype()){
            case 1->FACADE_GLASS_TOWER;
            case 4->FACADE_INDUSTRIAL;
            case 5->FACADE_FARM;
            case 6,7,8->FACADE_CIVIC;
            default->Math.floorMod(Long.hashCode(building.sourceId()),3);
        };
    }

    public static boolean roadNeedsPavedJoint(QatarPack.Road road){return road.roadClass()<=6;}
    public static boolean roadNeedsShoulder(QatarPack.Road road){return road.roadClass()<=4;}
    public static float shoulderWidth(QatarPack.Road road){return road.width()+(road.roadClass()<=2?3.0f:2.2f);}
    public static boolean roadNeedsCentreMarks(QatarPack.Road road){return road.roadClass()<=4||road.roadClass()==10;}
    public static float centreMarkWidth(QatarPack.Road road){return road.roadClass()==10?.65f:road.roadClass()<=2?.20f:.15f;}
    public static float dashLength(QatarPack.Road road){return road.roadClass()==10?18f:road.roadClass()<=2?7f:5f;}
    public static float dashGap(QatarPack.Road road){return road.roadClass()==10?14f:road.roadClass()<=2?5f:4f;}

    public static boolean rooftopEquipment(QatarPack.Building building){
        return building.height()>=8&&building.width()>=6&&building.depth()>=6&&building.archetype()!=5&&building.archetype()!=6;
    }
    public static boolean mosqueDetails(QatarPack.Building building){return building.archetype()==6&&building.width()>=5&&building.depth()>=5;}
    public static boolean parapet(QatarPack.Building building){return building.hasSourceFootprint()&&building.footprint().length<=48&&building.height()>=4;}
}
