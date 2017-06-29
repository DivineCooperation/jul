package org.openbase.jul.extension.rst.transform;

/*
 * #%L
 * JUL Extension RST Transform
 * %%
 * Copyright (C) 2015 - 2017 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
import org.openbase.jul.exception.CouldNotTransformException;
import rst.vision.HSBColorType.HSBColor;
import rst.vision.RGBColorType.RGBColor;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class HSBColorToRGBColorTransformer {

    public static HSBColor transform(RGBColor rgbColor) throws CouldNotTransformException {
        try {
            double hue, saturation, brightness;
            int r = rgbColor.getRed();
            int g = rgbColor.getGreen();
            int b = rgbColor.getBlue();

            int cmax = (r > g) ? r : g;
            if (b > cmax) cmax = b;
            int cmin = (r < g) ? r : g;
            if (b < cmin) cmin = b;

            brightness = ((double) cmax) / 255.0d;
            if (cmax != 0)
                saturation = ((double) (cmax - cmin)) / ((double) cmax);
            else
                saturation = 0;
            if (saturation == 0)
                hue = 0;
            else {
                double redc = ((double) (cmax - r)) / ((double) (cmax - cmin));
                double greenc = ((double) (cmax - g)) / ((double) (cmax - cmin));
                double bluec = ((double) (cmax - b)) / ((double) (cmax - cmin));
                if (r == cmax)
                    hue = bluec - greenc;
                else if (g == cmax)
                    hue = 2.0d + redc - bluec;
                else
                    hue = 4.0d + greenc - redc;
                hue = hue / 6.0d;
                if (hue < 0)
                    hue = hue + 1.0d;
            }

            hue *= 360.0d;
            saturation *= 100.0d;
            brightness *= 100.0d;

            return HSBColor.newBuilder().setHue(hue).setSaturation(saturation).setBrightness(brightness).build();
        } catch (Exception ex) {
            throw new CouldNotTransformException("Could not transform " + RGBColor.class.getName() + " to " + HSBColor.class.getName() + "!", ex);
        }
    }

    public static RGBColor transform(HSBColor hsbColor) throws CouldNotTransformException {
        try {
            int r = 0, g = 0, b = 0;
            double hue = hsbColor.getHue()/360.0d;
            double saturation = hsbColor.getSaturation()/100.0d;
            double brightness = hsbColor.getBrightness()/100.0d;

            if (saturation == 0) {
                r = g = b = (int) (brightness * 255.0d + 0.5d);
            } else {
                double h = (hue - Math.floor(hue)) * 6.0d;
                double f = h - Math.floor(h);
                double p = brightness * (1.0d - saturation);
                double q = brightness * (1.0d - saturation * f);
                double t = brightness * (1.0d - (saturation * (1.0d - f)));
                switch ((int) h) {
                    case 0:
                        r = (int) (brightness * 255.0d + 0.5d);
                        g = (int) (t * 255.0d + 0.5d);
                        b = (int) (p * 255.0d + 0.5d);
                        break;
                    case 1:
                        r = (int) (q * 255.0d + 0.5d);
                        g = (int) (brightness * 255.0d + 0.5d);
                        b = (int) (p * 255.0d + 0.5d);
                        break;
                    case 2:
                        r = (int) (p * 255.0d + 0.5d);
                        g = (int) (brightness * 255.0d + 0.5d);
                        b = (int) (t * 255.0d + 0.5d);
                        break;
                    case 3:
                        r = (int) (p * 255.0d + 0.5d);
                        g = (int) (q * 255.0d + 0.5d);
                        b = (int) (brightness * 255.0d + 0.5d);
                        break;
                    case 4:
                        r = (int) (t * 255.0d + 0.5d);
                        g = (int) (p * 255.0d + 0.5d);
                        b = (int) (brightness * 255.0d + 0.5d);
                        break;
                    case 5:
                        r = (int) (brightness * 255.0d + 0.5d);
                        g = (int) (p * 255.0d + 0.5d);
                        b = (int) (q * 255.0d + 0.5d);
                        break;
                }
            }
            return RGBColor.newBuilder().setRed(r).setGreen(g).setBlue(b).build();
        } catch (Exception ex) {
            throw new CouldNotTransformException("Could not transform " + HSBColor.class.getName() + " to " + RGBColor.class.getName() + "!", ex);
        }
    }
}
