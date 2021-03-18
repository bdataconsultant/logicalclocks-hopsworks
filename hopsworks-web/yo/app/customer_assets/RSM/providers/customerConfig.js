(function(angular) {
    "use strict";
    angular.module("hopsWorksApp").provider("customerConfig", function() {
      var values = {
        showPonFooter: true,
      };
  
      return {
        $get: function() {
          return values;
        },
        set: function(constants) {
          angular.extend(values, constants);
        }
      };
    });
  })(angular);
  