# Video Compressor
Video compression library for Android using kotlin. Based on the code from [Jorge E. Hernandez (@lalongooo)].


Description
--------
#### Video
Due to the high resolution of our Smartphone cameras and cameras from other devices, Video files have become large in size and thus difficult for it to be shared with others on social apps, social media and even when we need to upload it on our server. With SiliCompressor you can now compress you video file while maintaining it quality.


Usage
--------
To effectively use this library, you must make sure you have added the following permission to your project.
```java
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```
#### Compress a video file and return the file path of the new video
```java
String filePath = SiliCompressor.with(Context).compressVideo(videoPath, destinationDirectory);
```

Download
--------
#### Gradle
```groovy
implementation 'com.github.JordanSB:VideoCompressor:v1.39'
```

License
--------
Copyright 2019

Licensed under the Apache License, Version 2.0 (the "License")

you may not use this file except in compliance with the Licenses.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
