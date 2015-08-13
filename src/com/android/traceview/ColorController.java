package com.android.traceview;

import java.util.HashMap;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class ColorController {
	private static final int[] systemColors = { 9, 3, 5, 13, 11, 10, 4, 6, 8,
			14, 12, 2 };

	private static RGB[] rgbColors = { new RGB(90, 90, 255),
			new RGB(0, 240, 0), new RGB(255, 0, 0), new RGB(0, 255, 255),
			new RGB(255, 80, 255), new RGB(200, 200, 0), new RGB(40, 0, 200),
			new RGB(150, 255, 150), new RGB(150, 0, 0), new RGB(30, 150, 150),
			new RGB(200, 200, 255), new RGB(0, 120, 0), new RGB(255, 150, 150),
			new RGB(140, 80, 140), new RGB(150, 100, 50), new RGB(70, 70, 70) };

	private static HashMap<Integer, Color> colorCache = new HashMap<Integer, Color>();
	private static HashMap<Integer, Image> imageCache = new HashMap<Integer, Image>();

	public static Color requestColor(Display display, RGB rgb) {
		return requestColor(display, rgb.red, rgb.green, rgb.blue);
	}

	public static Image requestColorSquare(Display display, RGB rgb) {
		return requestColorSquare(display, rgb.red, rgb.green, rgb.blue);
	}

	public static Color requestColor(Display display, int red, int green,
			int blue) {
		int key = red << 16 | green << 8 | blue;
		Color color = (Color) colorCache.get(Integer.valueOf(key));
		if (color == null) {
			color = new Color(display, red, green, blue);
			colorCache.put(Integer.valueOf(key), color);
		}
		return color;
	}

	public static Image requestColorSquare(Display display, int red, int green,
			int blue) {
		int key = red << 16 | green << 8 | blue;
		Image image = (Image) imageCache.get(Integer.valueOf(key));
		if (image == null) {
			image = new Image(display, 8, 14);
			GC gc = new GC(image);
			Color color = requestColor(display, red, green, blue);
			gc.setBackground(color);
			gc.fillRectangle(image.getBounds());
			gc.dispose();
			imageCache.put(Integer.valueOf(key), image);
		}
		return image;
	}

	public static void assignMethodColors(Display display, MethodData[] methods) {
		int nextColorIndex = 0;
		for (MethodData md : methods) {
			RGB rgb = rgbColors[nextColorIndex];
			nextColorIndex++;
			if (nextColorIndex == rgbColors.length)
				nextColorIndex = 0;
			Color color = requestColor(display, rgb);
			Image image = requestColorSquare(display, rgb);
			md.setColor(color);
			md.setImage(image);

			int fadedRed = 150 + rgb.red / 4;
			int fadedGreen = 150 + rgb.green / 4;
			int fadedBlue = 150 + rgb.blue / 4;
			RGB faded = new RGB(fadedRed, fadedGreen, fadedBlue);
			color = requestColor(display, faded);
			image = requestColorSquare(display, faded);
			md.setFadedColor(color);
			md.setFadedImage(image);
		}
	}
}

/*
 * Location:
 * /Users/frank/Applications/android-sdk-macosx/tools/lib/traceview.jar
 * !/com/android/traceview/ColorController.class Java compiler version: 6 (50.0)
 * JD-Core Version: 0.7.1
 */