package io.anuke.mindustry.world.blocks.types.generation;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Layer;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.types.PowerBlock;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.graphics.Shapes;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Strings;
import io.anuke.ucore.util.Translator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.tilesize;
import static io.anuke.mindustry.Vars.world;

public class PowerDistributor extends PowerBlock{
	public static final float thicknessScl = 0.7f;
    public static final float flashScl = 0.07f;
	public static final float laserMinValue = 0f;
	public static final Color laserFrom = Color.valueOf("e3e3e3");
	public static final Color laserTo = Color.valueOf("ffe7a8");

	//last distribution block placed
	private static int lastPlaced = -1;

	protected Translator t1 = new Translator();
	protected Translator t2 = new Translator();

	public int laserRange = 6;
	public float powerSpeed = 0.5f;

	public PowerDistributor(String name){
		super(name);
		expanded = true;
		layer = Layer.power;
		hasInventory = false;
	}

	@Override
	public void placed(Tile tile) {
		Tile before = world.tile(lastPlaced);
		if(linkValid(tile, before)){
			tile.<DistributorEntity>entity().links.add(before.packedPosition());
		}

		lastPlaced = tile.packedPosition();
	}

	@Override
	public boolean isConfigurable(Tile tile){
	    return true;
    }

	@Override
	public void setStats(){
		super.setStats();

		stats.add("lasertilerange", laserRange);
		stats.add("maxpowertransfersecond", Strings.toFixed(powerSpeed * 60, 2));
	}

	@Override
	public void update(Tile tile){
        distributeLaserPower(tile);
    }

    @Override
    public boolean onConfigureTileTapped(Tile tile, Tile other){
		DistributorEntity entity = tile.entity();

		if(linkValid(tile, other)){
			if(entity.links.contains(other.packedPosition())){
				entity.links.removeValue(other.packedPosition());
			}else{
				entity.links.add(other.packedPosition());
			}
			return false;
		}
		return true;
	}

	@Override
	public void drawSelect(Tile tile){
		super.drawSelect(tile);

        Draw.color("power");
        Lines.stroke(1f);

        Lines.poly(Edges.getPixelPolygon(laserRange), tile.worldx() - tilesize/2, tile.worldy() - tilesize/2, tilesize);

        Draw.reset();
	}

	@Override
	public void drawConfigure(Tile tile){
		Draw.color("accent");

		Lines.stroke(1f);
		Lines.square(tile.drawx(), tile.drawy(),
				tile.block().size * tilesize / 2f + 1f);

		Lines.stroke(1f);

		Lines.poly(Edges.getPixelPolygon(laserRange), tile.worldx() - tilesize/2, tile.worldy() - tilesize/2, tilesize);

		Draw.color("power");

		for(int x = tile.x - laserRange; x <= tile.x + laserRange; x ++){
			for(int y = tile.y - laserRange; y <= tile.y + laserRange; y ++){
				Tile link = world.tile(x, y);
				if(link != tile && linkValid(tile, link)){
					Lines.square(link.drawx(), link.drawy(),
							link.block().size * tilesize / 2f + 1f);
				}
			}
		}

		Draw.reset();
	}

	@Override
	public void drawPlace(int x, int y, int rotation, boolean valid){
        Draw.color("place");
        Lines.stroke(1f);

        Lines.poly(Edges.getPixelPolygon(laserRange), x * tilesize - tilesize/2, y * tilesize - tilesize/2, tilesize);

        Draw.reset();
	}

	@Override
	public void drawLayer(Tile tile){
		if(!Settings.getBool("lasers")) return;

		DistributorEntity entity = tile.entity();

		if(entity.power.amount > powerSpeed){
			entity.laserColor = Mathf.lerpDelta(entity.laserColor, 1f, 0.05f);
		}else{
			entity.laserColor = Mathf.lerpDelta(entity.laserColor, laserMinValue, 0.05f);
		}

		Draw.color(laserFrom, laserTo, entity.laserColor * (1f-flashScl) + Mathf.sin(Timers.time(), 1.7f, flashScl));

		for(int i = 0; i < entity.links.size; i ++){
			Tile link = world.tile(entity.links.get(i));
		    if(linkValid(tile, link)) drawLaser(tile, link);
        }

		Draw.color();
	}

	protected void distributeLaserPower(Tile tile){
		DistributorEntity entity = tile.entity();

		//TODO implement
	}

	protected boolean linkValid(Tile tile, Tile link){
		return tile != link && link != null && link.block() instanceof PowerDistributor &&
				Vector2.dst(tile.worldx(), tile.worldy(), link.worldx(), link.worldy()) < Math.max(laserRange * tilesize,
						((PowerDistributor)link.block()).laserRange * tilesize);
	}

	protected void drawLaser(Tile tile, Tile target){
        float x1 = tile.drawx(), y1 = tile.drawy(),
                x2 = target.drawx(), y2 = target.drawy();

        float angle1 = Angles.angle(x1, y1, x2, y2);
        float angle2 = angle1 + 180f;

        t1.trns(angle1, tile.block().size * tilesize/2f + 1f);
        t2.trns(angle2,tile.block().size * tilesize/2f + 1f);

        Shapes.laser("laser", "laser-end", x1 + t1.x, y1 + t1.y,
                x2 + t2.x, y2 + t2.y, thicknessScl);
	}

    @Override
    public TileEntity getEntity() {
        return new DistributorEntity();
    }

    public static class DistributorEntity extends TileEntity{
        public float laserColor = laserMinValue;
        public IntArray links = new IntArray();

		@Override
		public void write(DataOutputStream stream) throws IOException {
			stream.writeShort(links.size);
			for(int i = 0; i < links.size; i ++){
				stream.writeInt(links.get(i));
			}
		}

		@Override
		public void read(DataInputStream stream) throws IOException {
			short amount = stream.readShort();
			for(int i = 0; i < amount; i ++){
				links.add(stream.readInt());
			}
		}
	}

}