[![Build Status](https://travis-ci.org/eyeem/zeppelin.png)](https://travis-ci.org/eyeem/zeppelin)

Zeppelin Library
=================

Potato's complimentary package. Handles interactions between UI, network and storage layers.

Usage
============
Let's say you have defined a custom Poll class, e.g.HomeTimelinePoll which fetches your own tweets. You can now effortlessly bind it with a PollListView in order to manage content fetching, e.g. automated refreshes, infinite scroll.

``` java
// ...and bind the Poll with PollListView

class TimelineActivity extends Activity {

   class HomeTimelinePoll extends Poll<Tweet> {
      // ...API CALLS HERE
   }

   PollListView listView;
   HomeTimelinePoll poll;

   @Override
   protected void onCreate(Bundle bundle) {
      super.onCreate(bundle);

      // setup poll
      poll = new HomeTimelinePoll();
      poll.setStorage(TweetStorage.getInstance().obtainList("home_timeline"));

      // setup list view
      listView = (PollListView) findViewById(R.id.myListView);
      listView.setPoll(poll);
      listView.setDataAdapter(new TweetAdapter());
   }

   @Override
   protected void onResume() {
      listView.onResume();
   }

   @Override
   protected void onPause() {
      listView.onPause();
   }
}



```

Including in your project
=========================

You can either check out the repo manually or grab a snapshot `aar` which is hosted on sonatype repo. To do so, include this in your build.gradle file:

```
dependencies {

    repositories {
        maven {
            url 'https://oss.sonatype.org/content/repositories/snapshots/'
        }
        mavenCentral()
        mavenLocal()
    }

    compile 'com.eyeem.zeppelin:library:0.9.0-SNAPSHOT@aar'

    // ...other dependencies
}
```

Developed By
============

* Lukasz Wisniewski
* Tobias Heine

License
=======

    Copyright 2012-2013 EyeEm Mobile GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
