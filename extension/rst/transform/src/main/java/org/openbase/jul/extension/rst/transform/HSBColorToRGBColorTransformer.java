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
import org.openbase.jul.exception.TypeNotSupportedException;
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

            brightness = ((float) cmax) / 255.0f;
            if (cmax != 0)
                saturation = ((float) (cmax - cmin)) / ((float) cmax);
            else
                saturation = 0;
            if (saturation == 0)
                hue = 0;
            else {
                float redc = ((float) (cmax - r)) / ((float) (cmax - cmin));
                float greenc = ((float) (cmax - g)) / ((float) (cmax - cmin));
                float bluec = ((float) (cmax - b)) / ((float) (cmax - cmin));
                if (r == cmax)
                    hue = bluec - greenc;
                else if (g == cmax)
                    hue = 2.0f + redc - bluec;
                else
                    hue = 4.0f + greenc - redc;
                hue = hue / 6.0f;
                if (hue < 0)
                    hue = hue + 1.0f;
            }
            return HSBColor.newBuilder().setHue(hue).setSaturation(saturation).setBrightness(brightness).build();
        } catch (Exception ex) {
            throw new CouldNotTransformException("Could not transform " + RGBColor.class.getName() + " to " + HSBColor.class.getName() + "!", ex);
        }
    }

    public static RGBColor transform(HSBColor hsbColor) throws TypeNotSupportedException, CouldNotTransformException {
        try {
            int r = 0, g = 0, b = 0;
            double hue = hsbColor.getHue();
            double saturation = hsbColor.getSaturation();
            double brightness = hsbColor.getBrightness();

            if (saturation == 0) {
                r = g = b = (int) (brightness * 255.0f + 0.5f);
            } else {
                double h = (hue - (float) Math.floor(hue)) * 6.0f;
                double f = h - (float) java.lang.Math.floor(h);
                double p = brightness * (1.0f - saturation);
                double q = brightness * (1.0f - saturation * f);
                double t = brightness * (1.0f - (saturation * (1.0f - f)));
                switch ((int) h) {
                    case 0:
                        r = (int) (brightness * 255.0f + 0.5f);
                        g = (int) (t * 255.0f + 0.5f);
                        b = (int) (p * 255.0f + 0.5f);
                        break;
                    case 1:
                        r = (int) (q * 255.0f + 0.5f);
                        g = (int) (brightness * 255.0f + 0.5f);
                        b = (int) (p * 255.0f + 0.5f);
                        break;
                    case 2:
                        r = (int) (p * 255.0f + 0.5f);
                        g = (int) (brightness * 255.0f + 0.5f);
                        b = (int) (t * 255.0f + 0.5f);
                        break;
                    case 3:
                        r = (int) (p * 255.0f + 0.5f);
                        g = (int) (q * 255.0f + 0.5f);
                        b = (int) (brightness * 255.0f + 0.5f);
                        break;
                    case 4:
                        r = (int) (t * 255.0f + 0.5f);
                        g = (int) (p * 255.0f + 0.5f);
                        b = (int) (brightness * 255.0f + 0.5f);
                        break;
                    case 5:
                        r = (int) (brightness * 255.0f + 0.5f);
                        g = (int) (p * 255.0f + 0.5f);
                        b = (int) (q * 255.0f + 0.5f);
                        break;
                }
            }
            return RGBColor.newBuilder().setRed(r).setGreen(g).setBlue(b).build();
        } catch (Exception ex) {
            throw new CouldNotTransformException("Could not transform " + HSBColor.class.getName() + " to " + RGBColor.class.getName() + "!", ex);
        }
    }
}
