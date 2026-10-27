import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Renders the Modrinth gallery banner (2:1, the ratio of Modrinth's search and app cards).
 * Run from the repository root: java docs/art/Banner.java [logo.png] [out.png]
 * Defaults: docs/brand/icon-512.png -> docs/banner.png. Pixel-art logos are scaled with nearest neighbour.
 */
public class Banner {
    static final int W=1920, H=960;
    static final Color BACK=rgb(0x0D0D13), PANEL=rgb(0x1B1B22), EDGE=rgb(0x393943), TEXT=rgb(0xDEDEE6), MUTED=rgb(0xACA59A),
        GOLD=rgb(0xFFC665), CYAN=rgb(0x49F6F6), GREEN=rgb(0x40ED45), PURPLE=rgb(0xC08CFF), DIM=rgb(0x5C5966), SHADOW=rgb(0x2A2A35);

    /** 5x7 pixel font (narrower glyphs keep their own width); '#' is a lit cell. */
    static final Map<Character,String[]> FONT=Map.ofEntries(
        g('A',".###.","#...#","#...#","#####","#...#","#...#","#...#"), g('B',"####.","#...#","#...#","####.","#...#","#...#","####."),
        g('C',".###.","#...#","#....","#....","#....","#...#",".###."), g('D',"####.","#...#","#...#","#...#","#...#","#...#","####."),
        g('E',"#####","#....","#....","####.","#....","#....","#####"), g('F',"#####","#....","#....","####.","#....","#....","#...."),
        g('G',".####","#....","#....","#..##","#...#","#...#",".####"), g('H',"#...#","#...#","#...#","#####","#...#","#...#","#...#"),
        g('I',"###",".#.",".#.",".#.",".#.",".#.","###"), g('J',"..###","...#.","...#.","...#.","#..#.","#..#.",".##.."),
        g('K',"#...#","#..#.","#.#..","##...","#.#..","#..#.","#...#"), g('L',"#....","#....","#....","#....","#....","#....","#####"),
        g('M',"#...#","##.##","#.#.#","#.#.#","#...#","#...#","#...#"), g('N',"#...#","##..#","#.#.#","#..##","#...#","#...#","#...#"),
        g('O',".###.","#...#","#...#","#...#","#...#","#...#",".###."), g('P',"####.","#...#","#...#","####.","#....","#....","#...."),
        g('Q',".###.","#...#","#...#","#...#","#.#.#","#..#.",".##.#"), g('R',"####.","#...#","#...#","####.","#..#.","#...#","#...#"),
        g('S',".####","#....","#....",".###.","....#","....#","####."), g('T',"#####","..#..","..#..","..#..","..#..","..#..","..#.."),
        g('U',"#...#","#...#","#...#","#...#","#...#","#...#",".###."), g('V',"#...#","#...#","#...#","#...#",".#.#.",".#.#.","..#.."),
        g('W',"#...#","#...#","#...#","#.#.#","#.#.#","##.##","#...#"), g('X',"#...#","#...#",".#.#.","..#..",".#.#.","#...#","#...#"),
        g('Y',"#...#","#...#",".#.#.","..#..","..#..","..#..","..#.."), g('Z',"#####","....#","...#.","..#..",".#...","#....","#####"),
        g(' ',"...","...","...","...","...","...","..."));

    public static void main(String[] args) throws Exception {
        String logoPath=args.length>0?args[0]:"docs/brand/icon-512.png", outPath=args.length>1?args[1]:"docs/banner.png";
        BufferedImage logo=ImageIO.read(new File(logoPath));
        BufferedImage img=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        background(g);

        // Header: logo, then the name ("Sky" cyan as in the wordmark), vertically centred on the logo.
        int logoSize=256, title=12, gap=56, titleW=textWidth("SKYPROGRESSION",title);
        int left=(W-(logoSize+gap+titleW))/2, top=140;
        glow(g,left+logoSize/2,top+logoSize/2,300,CYAN,46);
        drawLogo(g,logo,left,top,logoSize);
        int tx=left+logoSize+gap, ty=top+(logoSize-7*title)/2-20;
        tx=text(g,"SKY",tx,ty,title,CYAN);
        text(g,"PROGRESSION",tx,ty,title,TEXT);
        int sub=4, subY=ty+7*title+36, sx=left+logoSize+gap;
        sx=text(g,"MINING PROGRESSION FOR ",sx,subY,sub,MUTED);
        text(g,"HYPIXEL SKYBLOCK",sx,subY,sub,GOLD);

        // Three cards hinting at the features: stat breakdown, rate per method, best upgrade per coin.
        int cw=480, ch=300, cg=48, cx=(W-(3*cw+2*cg))/2, cy=510;
        statsCard(g,cx,cy,cw,ch);
        ratesCard(g,cx+cw+cg,cy,cw,ch);
        upgradeCard(g,cx+2*(cw+cg),cy,cw,ch);
        g.dispose();
        ImageIO.write(img,"png",new File(outPath));
    }

    static void background(Graphics2D g) {
        g.setColor(BACK);g.fillRect(0,0,W,H);
        glow(g,W-260,H-120,620,PURPLE,26);
        glow(g,220,H+80,560,GOLD,14);
        // Faint block grid, like looking at a wall of stone from far away.
        g.setColor(new Color(255,255,255,7));
        for(int x=0;x<W;x+=64)g.fillRect(x,0,2,H);
        for(int y=0;y<H;y+=64)g.fillRect(0,y,W,2);
        // A few pixel sparkles kept clear of the content.
        int[][] sparkles={{120,96,0},{1800,140,1},{1700,64,2},{96,420,1},{1830,430,0},{60,820,2},{1860,860,1},{900,60,2},{1300,420,0}};
        Color[] tones={CYAN,PURPLE,GOLD};
        for(int[] s:sparkles)sparkle(g,s[0],s[1],8,alpha(tones[s[2]],110));
    }

    static void statsCard(Graphics2D g,int x,int y,int w,int h) {
        card(g,x,y,w,h,"STAT SOURCES",GOLD);
        Color[] parts={GOLD,CYAN,GREEN,PURPLE};
        int[][] rows={{150,90,46,40},{110,120,30,64},{180,52,64,0}};
        int bx=x+36, bw=w-72;
        for(int r=0;r<rows.length;r++){
            int by=y+100+r*62;
            g.setColor(BACK);g.fillRect(bx,by,bw,28);
            int px=bx;
            for(int p=0;p<parts.length;p++)if(rows[r][p]>0){g.setColor(parts[p]);g.fillRect(px,by,rows[r][p],28);g.setColor(new Color(255,255,255,70));g.fillRect(px,by,rows[r][p],4);px+=rows[r][p];}
            g.setColor(TEXT);g.fillRect(bx+bw-4,by-6,4,40); // attainable maximum
        }
    }

    static void ratesCard(Graphics2D g,int x,int y,int w,int h) {
        card(g,x,y,w,h,"RATE PER METHOD",CYAN);
        int[] lens={380,300,228,160};Color[] cols={CYAN,GOLD,DIM,DIM};Color[] gems={CYAN,PURPLE,GREEN,GOLD};
        for(int i=0;i<lens.length;i++){
            int by=y+96+i*48;
            gem(g,x+36,by-2,gems[i]);
            g.setColor(cols[i]);g.fillRect(x+76,by,lens[i]-12,24);
            g.setColor(new Color(255,255,255,70));g.fillRect(x+76,by,lens[i]-12,4);
        }
    }

    static void upgradeCard(Graphics2D g,int x,int y,int w,int h) {
        card(g,x,y,w,h,"BEST UPGRADE",GREEN);
        // Pixel arrow pointing up, drawn on an 8px grid.
        String[] arrow={"...#...","..###..",".#####.","#######","..###..","..###..","..###..","..###.."};
        int ax=x+40, ay=y+96, c=12;
        for(int r=0;r<arrow.length;r++)for(int k=0;k<arrow[r].length();k++)if(arrow[r].charAt(k)=='#'){
            g.setColor(SHADOW);g.fillRect(ax+k*c+c/2,ay+r*c+c/2,c,c);
        }
        for(int r=0;r<arrow.length;r++)for(int k=0;k<arrow[r].length();k++)if(arrow[r].charAt(k)=='#'){
            g.setColor(GREEN);g.fillRect(ax+k*c,ay+r*c,c,c);
        }
        // Rate before and after: the gain is the green extension; the coin marks "per coin".
        int bx=x+160, bw=w-196;
        g.setColor(BACK);g.fillRect(bx,y+112,bw,28);g.setColor(DIM);g.fillRect(bx,y+112,bw*55/100,28);
        g.setColor(BACK);g.fillRect(bx,y+164,bw,28);g.setColor(DIM);g.fillRect(bx,y+164,bw*55/100,28);
        g.setColor(GREEN);g.fillRect(bx+bw*55/100,y+164,bw*30/100,28);
        g.setColor(new Color(255,255,255,70));g.fillRect(bx,y+112,bw*55/100,4);g.fillRect(bx,y+164,bw*85/100,4);
        coin(g,bx,y+h-76);
        text(g,"PER COIN",bx+56,y+h-68,4,GOLD);
    }

    static void card(Graphics2D g,int x,int y,int w,int h,String label,Color accent) {
        g.setColor(new Color(0,0,0,90));g.fillRect(x+12,y+12,w,h);
        g.setColor(EDGE);g.fillRect(x,y,w,h);
        g.setColor(PANEL);g.fillRect(x+4,y+4,w-8,h-8);
        g.setColor(accent);g.fillRect(x+4,y+4,w-8,6);
        text(g,label,x+36,y+40,4,TEXT);
    }

    static void gem(Graphics2D g,int x,int y,Color c) {
        String[] s={".##.","####","####",".##."};
        for(int r=0;r<4;r++)for(int k=0;k<4;k++)if(s[r].charAt(k)=='#'){g.setColor(r==0||k==0?c.brighter():c.darker());g.fillRect(x+k*7,y+r*7,7,7);}
        g.setColor(Color.WHITE);g.fillRect(x+7,y+7,7,7);
    }

    static void coin(Graphics2D g,int x,int y) {
        String[] s={".###.","#hhh#","#h#d#","#hdd#",".###."};
        for(int r=0;r<5;r++)for(int k=0;k<5;k++){
            char ch=s[r].charAt(k);if(ch=='.')continue;
            g.setColor(ch=='h'?rgb(0xFFE3A8):ch=='d'?rgb(0xD99A3A):GOLD);g.fillRect(x+k*8,y+r*8,8,8);
        }
    }

    static void sparkle(Graphics2D g,int x,int y,int c,Color col) {
        g.setColor(col);g.fillRect(x-c/2,y-c*2+c/2,c,c*3);g.fillRect(x-c*2+c/2,y-c/2,c*3,c);
        g.setColor(alpha(col,40));g.fillRect(x-c/2,y-c*3+c/2,c,c*5);g.fillRect(x-c*3+c/2,y-c/2,c*5,c);
    }

    static void drawLogo(Graphics2D g,BufferedImage logo,int x,int y,int size) {
        boolean pixelArt=logo.getWidth()<=64||logo.getWidth()%16==0&&countColours(logo)<=32;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,pixelArt?RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR:RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(logo,x,y,size,size,null);
    }

    static int countColours(BufferedImage img) {
        var seen=new java.util.HashSet<Integer>();
        for(int y=0;y<img.getHeight();y+=4)for(int x=0;x<img.getWidth();x+=4)if(seen.add(img.getRGB(x,y))&&seen.size()>32)return seen.size();
        return seen.size();
    }

    static void glow(Graphics2D g,int cx,int cy,int r,Color c,int a) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx,cy),r,new float[]{0f,1f},new Color[]{alpha(c,a),alpha(c,0)}));
        g.fillOval(cx-r,cy-r,2*r,2*r);
    }

    /** Draws pixel text with a one-cell drop shadow; returns the x after the last glyph. */
    static int text(Graphics2D g,String s,int x,int y,int cell,Color c) {
        for(char ch:s.toCharArray()){
            String[] rows=FONT.get(ch);
            for(int pass=0;pass<2;pass++){
                g.setColor(pass==0?SHADOW:c);int o=pass==0?cell:0;
                for(int r=0;r<7;r++)for(int k=0;k<rows[r].length();k++)if(rows[r].charAt(k)=='#')g.fillRect(x+k*cell+o,y+r*cell+o,cell,cell);
            }
            x+=(rows[0].length()+1)*cell;
        }
        return x;
    }

    static int textWidth(String s,int cell) {
        int w=0;for(char ch:s.toCharArray())w+=(FONT.get(ch)[0].length()+1)*cell;
        return w-cell;
    }

    static Map.Entry<Character,String[]> g(char c,String... rows){return Map.entry(c,rows);}
    static Color rgb(int v){return new Color(v);}
    static Color alpha(Color c,int a){return new Color(c.getRed(),c.getGreen(),c.getBlue(),a);}
}
