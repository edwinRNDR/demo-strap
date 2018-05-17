STRAP
LIMP NINJA

all things Bruce.

released at Outline 2018, ranked first

Recycles parts of the Paddock codebase.

Uses Cooper Hewitt typeface
Uses environment maps from noemotionhdrs.net
Uses purchased stock art meshes from [I forgot where]
Uses BASS and its NativeBass JVM bindings
Uses color grading templates found in Adobe Photoshop's color grading tools
Uses OpenStreetsMap maps
Uses subtitles generated from Twin Peaks (seasons 1 and 2) subtitles
Uses Java 9.0.1
Uses Kotlin 1.2.31
Uses OPENRNDR 0.3.13

Runs at acceptable framerates on a XPS 15 9560 (Intel i7 7700HQ, NVidia GTX 1050)

This all started with a simple gradient descent based solver for IK. I tried to apply the solver to generate real-time
gait animations, which is fun but much harder to get right than I had initially thought. The narrative in Strap is a bit
under developed because it was written by the process of merging small experiments together.

First time I worked HDR skyboxes and irradiance maps but in the end most of it turned out to be too dim and SDR looking.
First time I worked with shadow maps, turned out OK but I couldn't use them in the way I wanted (self shadowing on the
human shaped meshes).
First time I worked with normal maps, that's mostly OK looking but the SSLR has a lot of temporal aliasing, which looks
both good and bad.
First time I worked with IK, it still looks clumsy and it is running in real-time for no good reason. Better to precalc
it next time and use the gained stability for creating more complex animations.