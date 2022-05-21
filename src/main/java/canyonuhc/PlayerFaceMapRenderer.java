package canyonuhc;

import java.awt.Image;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class PlayerFaceMapRenderer extends MapRenderer {
    private final Player player;
    private boolean rendered;

    public PlayerFaceMapRenderer(Player player) {
        this.player = player;
        this.rendered = false;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player ignored) {
        if (this.rendered) return;
        try {
            URL craftHeadUrl = new URL("http://s3.amazonaws.com/MinecraftSkins/" + player.getName() + ".png");
            Image face = ImageIO.read(craftHeadUrl);
            canvas.drawImage(-128, -128, face.getScaledInstance(1024, 512, 0));
            this.rendered = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
