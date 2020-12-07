# HEIF-converter

![GitHub release (latest by date)](https://img.shields.io/github/v/release/lincollincol/HEIF-converter)
![GitHub](https://img.shields.io/github/license/lincollincol/HEIF-converter)  
![GitHub followers](https://img.shields.io/github/followers/lincollincol?style=social)
![GitHub stars](https://img.shields.io/github/stars/lincollincol/HEIF-converter?style=social)
![GitHub forks](https://img.shields.io/github/forks/lincollincol/HEIF-converter?style=social)

<p align="center">
  <img src="https://github.com/lincollincol/HEIF-converter/blob/master/img/header.png" width="550" height="250">
</p>  

Converter for High Efficiency Image Format(HEIF) to other image format

## Available formats
* JPEG  
* PNG  
* WEBP  

## Download
### Gradle
``` groovy
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
``` groovy
dependencies {
  implementation 'com.github.lincollincol:HEIF-converter:1.2'
}
```  

### Maven
``` xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```
``` xml
<dependency>
  <groupId>com.github.lincollincol</groupId>
  <artifactId>Repo</artifactId>
  <version>1.2</version>
</dependency>
```

## Usage
``` kotlin
HeifConverter.useContext(this)
                .fromUrl("https://github.com/nokiatech/heif/raw/gh-pages/content/images/crowd_1440x960.heic")
                .withOutputFormat(HeifConverter.Format.PNG)
                .withOutputQuality(100) // optional - default value = 100. Available range (0 .. 100)
                .saveFileWithName("Image_Converted_Name_2") // optional - default value = uuid random string
                .saveResultImage(true) // optional - default value = true
                .convert {
                    println(it[HeifConverter.Key.IMAGE_PATH] as String)
                    resultImage.setImageBitmap((it[HeifConverter.Key.BITMAP] as Bitmap))
                }
```
### convert function
* Lambda, inside convert method, will return map of Objects. 
If you want to get result bitmap, you need to get value by libarry key ``` HeifConverter.Key.BITMAP ``` and cast it to Bitmap

* If you need path to converted image - use ``` HeifConverter.Key.IMAGE_PATH ``` key and cast map value to String.

### saveResultImage function
* Set false, if you need bitmap only without saving.
* You can skip this function if you want to save converted image, because it is true by default

### saveFileWithName function
* Use custom file name.
* Skip this function if you don't need custom converted image name, because UUID generate unique name by default. 

### withOutputQuality function
* Use this function if you need custom output quality.
* Skip this function if you don't need custom output quality, default quality - 100

### withOutputFormat function
* Set output image format.
* Use values from HeifConverter.Format. (Current available: PNG, JPEG, WEBP).

### from (Source)
* Convert heic image from sources such file, url, bytes, input stream etc.
* You can call this function only one time. If you call this function few times - converter will use last called source.

## Based on
<a href="https://github.com/yohhoy/heifreader">heifreader by yohhoy</a>

## License
```
MIT License

Copyright (c) 2020 lincollincol

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
