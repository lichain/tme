module Portal
    class Monitor < Sinatra::Base
        register Sinatra::Contrib
        register Sinatra::RespondWith
        register Sinatra::Head

        respond_to :html, :json, :xml, :js

        set :rrddir, (ENV["rrddir"] || "/tmp/rrd/rrd")
        set :canvas, Portal::Declare.new

        helpers do
            def link_to(name, url)
                "<a href='#{url}'>#{name}</a>"
            end

            def js(template)
                coffee erb("#{template.to_s}.js".intern, :layout => false)
            end
        end

        get "/" do
            redirect "/exchanges"
        end

        get "/exchanges" do
            @json = Pow(settings.rrddir).glob("*.json").map do |file|
                JSON.parse( File.read(file), :symbolize_names => true )
            end
            respond_to do |format|
                format.html { erb :"exchanges/list" }
                format.json {
                    @json.map { |data|
                        url("exchanges/#{File.basename data[:rrd].sub(/\.rrd$/, "")}")
                    }.to_json
                }
            end
        end
        
        get "/exchanges/:name" do
            @name = "#{settings.rrddir}/#{params[:name]}"
            @file = @name + ".rrd"

			@range = request.cookies["range"]
			@range ||= "10.minutes"
	    json = JSON.parse( File.read(@name+".json"), :symbolize_names => true )
	    @lastupdate = Time.at(json[:timestamp]/1000)

            settings.canvas.pictures.each do |label, pic|
                pic.graph(label, @file, @range)
            end

            respond_to do |format|
                format.html { 
		    if File.exist?(@name + ".archive") then
			@archive = JSON.parse('[' + File.read(@name + ".archive")[0..-3] + ']', :symbolize_names => true)
		    else
			@archive = []
		    end
                    @metrics = json[:metrics]
                    @consumers = json[:consumers]
                    @producers = json[:producers]
                    erb :"exchanges/show" 
                }
                format.json { 
                    if not File.exist?(@name + ".json")
                        status 404
                        "Requested exchange #{params[:name]} not found!"
                    else
                        json[:metrics].to_json
                    end
                }
                format.js {
                    @metrics = json[:metrics]
                    @consumers = json[:consumers]
                    @producers = json[:producers]
                    js :"exchanges/show"
                }
            end
        end

	post "/merge" do
	    @range = request.cookies["range"]
	    @range ||= "10.minutes"
	    @imgs = []

            settings.canvas.pictures.each do |label, pic|
                img = pic.merge(label, settings.rrddir, @params[:selected], @range)
		@imgs.push(img)
            end

	    respond_to do |format|
		format.js {
		    js :"merge"
		}
	    end
	end

        get "/daily" do
            "daily"
        end

        get "/notification" do
            "notification"
        end

        get "/debug" do
            settings.canvas.inspect
        end

    end
end
