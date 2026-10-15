package ai.arena.qatar.render;

import ai.arena.qatar.world.QatarPack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QatarVisualStyleTest {
    private static QatarPack.Building building(int archetype,long id,float width,float depth,float height){
        return new QatarPack.Building(10,20,width,depth,height,0,archetype,0,id,new float[]{0,0,width,0,width,depth,0,depth});
    }
    private static QatarPack.Road road(int roadClass,float width){return new QatarPack.Road(0,0,60,0,roadClass,2,width);}

    @Test public void facadePalettesAreQatarSpecificAndDeterministic(){
        assertEquals(QatarVisualStyle.FACADE_GLASS_TOWER,QatarVisualStyle.facadePalette(building(1,10,20,20,40)));
        assertEquals(QatarVisualStyle.FACADE_CIVIC,QatarVisualStyle.facadePalette(building(6,11,18,15,12)));
        int first=QatarVisualStyle.facadePalette(building(3,12345,12,9,7));
        assertEquals(first,QatarVisualStyle.facadePalette(building(3,12345,12,9,7)));
        assertTrue(first>=0&&first<=2);
    }

    @Test public void roadAndRoofDetailRulesStayBounded(){
        assertTrue(QatarVisualStyle.roadNeedsShoulder(road(2,16)));
        assertFalse(QatarVisualStyle.roadNeedsShoulder(road(5,7.2f)));
        assertTrue(QatarVisualStyle.shoulderWidth(road(2,16))>16);
        assertTrue(QatarVisualStyle.roadNeedsCentreMarks(road(4,10)));
        assertFalse(QatarVisualStyle.roadNeedsCentreMarks(road(5,7.2f)));
        assertTrue(QatarVisualStyle.MAX_ROAD_JOINTS_PER_CELL<=384);
        assertTrue(QatarVisualStyle.ROAD_JOINT_DIVISIONS<=8);
        assertTrue(QatarVisualStyle.MAX_MARKING_STRIPS_PER_CELL*6<16384);
        assertTrue(QatarVisualStyle.MAX_ROAD_SUBDIVISIONS<=16);
        assertTrue(QatarVisualStyle.rooftopEquipment(building(1,1,20,20,30)));
        assertTrue(QatarVisualStyle.mosqueDetails(building(6,2,20,15,12)));
        assertTrue(QatarVisualStyle.parapet(building(3,3,10,8,7)));
        assertTrue(QatarVisualStyle.facadeTrim(building(3,4,10,8,7)));
    }
}
