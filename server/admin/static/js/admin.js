var Admin = {
  init: function() {
      $(document).ready(function() {
          $.ajax({
              type: "GET",
              dataType: "json",
              url: "/open",
              success: function(data) {
                  $("#admin").text(JSON.stringify(data));
              }
          });
      });
  }
};