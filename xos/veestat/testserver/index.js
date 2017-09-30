var data = {
  "dm": {
    "dmId": 1,
    "dmCfgType": "DMDMM",
    "version": "Y17312008",
    "startTime": "immediate",
    "stopTime": "none",
    "current": {
      "startTime": "2017-03-26T21:53:42.304+01:00",
      "elapsedTime": "PT2M3S",
      "suspectStatus": "false",
      "FrameDelayRangeTwoWayMin": "PT0.00005S",
      "FrameDelayRangeTwoWayMax": "PT0.00007S",
      "FrameDelayRangeTwoWayAvg": "PT0.00006S",
      "FrameDelayRangeTwoWayBins": {
        "bins": [
          {
            "lowerLimit": "PT0S", // 0 - 10 ms
            "count": 10
          },
          {
            "lowerLimit": "PT0.000005S",  // 10 - 20 ms
            "count": 15
          },
          {
            "lowerLimit": "PT0.00001S", // 20 - 37 ms
            "count": 10
          },
          {
            "lowerLimit": "PT0.00002S", // > 37 ms
            "count": 3
          }
        ]
      },
      "interFrameDelayVariationTwoWayMin": "PT0.000003S",
      "interFrameDelayVariationTwoWayMax": "PT0.000005S",
      "interFrameDelayVariationTwoWayAvg": "PT0.000004S",
      "interFrameDelayVariationTwoWayBins": {
        "bins": [
          {
            "lowerLimit": "PT0S",
            "count": 10 // 0 - 3 ms
          },
          {
            "lowerLimit": "PT0.000005S",
            "count": 15 // 3 - 8 ms
          },
          {
            "lowerLimit": "PT0.00001S",
            "count": 10 // 8 - 100 ms
          },
          {
            "lowerLimit": "PT0.00002S",
            "count": 3 // > 100 ms
          }
        ]
      },
      "soamPdusReceived": "25",
      "soamPdusSent": "25"
    },
    "historic": [
      {
        "historyId": "1",
        "endTime": "2017-03-26T21:37:45.304+01:00",
        "elapsedTime": "PT3M",
        "suspectStatus": "false",
        "FrameDelayRangeTwoWayMin": "PT0.00001S",
        "FrameDelayRangeTwoWayMax": "PT0.00003S",
        "FrameDelayRangeTwoWayAvg": "PT0.00002S",
        "FrameDelayRangeTwoWayBins": {
          "bins": [
            {
              "lowerLimit": "PT0S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.000005S",
              "count": 15
            },
            {
              "lowerLimit": "PT0.00001S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.00002S",
              "count": 3
            }
          ]
        },
        "interFrameDelayVariationTwoWayMin": "PT0.000003S",
        "interFrameDelayVariationTwoWayMax": "PT0.000005S",
        "interFrameDelayVariationTwoWayAvg": "PT0.000004S",
        "interFrameDelayVariationTwoWayBins": {
          "bins": [
            {
              "lowerLimit": "PT0S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.000005S",
              "count": 15
            },
            {
              "lowerLimit": "PT0.00001S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.00002S",
              "count": 3
            }
          ]
        },
        "soamPdusReceived": "25",
        "soamPdusSent": "25"
      },
      {
        "historyId": "2",
        "endTime": "2017-03-26T21:27:45.304+01:00",
        "elapsedTime": "PT3M",
        "suspectStatus": "false",
        "FrameDelayRangeTwoWayMin": "PT0.00001S",
        "FrameDelayRangeTwoWayMax": "PT0.00003S",
        "FrameDelayRangeTwoWayAvg": "PT0.00002S",
        "FrameDelayRangeTwoWayBins": {
          "bins": [
            {
              "lowerLimit": "PT0S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.000005S",
              "count": 15
            },
            {
              "lowerLimit": "PT0.00001S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.00002S",
              "count": 3
            }
          ]
        },
        "interFrameDelayVariationTwoWayMin": "PT0.000003S",
        "interFrameDelayVariationTwoWayMax": "PT0.000005S",
        "interFrameDelayVariationTwoWayAvg": "PT0.000004S",
        "interFrameDelayVariationTwoWayBins": {
          "bins": [
            {
              "lowerLimit": "PT0S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.000005S",
              "count": 15
            },
            {
              "lowerLimit": "PT0.00001S",
              "count": 10
            },
            {
              "lowerLimit": "PT0.00002S",
              "count": 3
            }
          ]
        },
        "soamPdusReceived": "25",
        "soamPdusSent": "25"
      }
    ]
  }
};

var http = require('http');


var app = http.createServer(function(req,res){
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', 'http://localhost:3000');
  res.setHeader('Access-Control-Allow-Methods', 'GET');
  res.setHeader('Access-Control-Allow-Headers', 'X-Requested-With, content-type, x-xossession, x-sessionid');
  res.end(JSON.stringify(data));
});
console.log("starting server on port 3003...");
app.listen(3003);