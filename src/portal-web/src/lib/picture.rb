module Portal
    class Picture

        attr_accessor :lines

        def initialize
            @lines = []
        end

        def generate(rrdfile)
            result = []
            result.concat to_def(rrdfile)
            result.concat to_line
            result
        end

        def to_def(rrdfile)
            result = []
            @lines.each do |line|
                result.push "DEF:#{line[:label]}=#{rrdfile}:#{line[:datasource]}:#{line[:consolid]}"
            end
            result
        end

        def to_line
            result = []
            @lines.each_with_index do |line, i|
                result.push "LINE#{line[:width]}:#{line[:label]}#{line[:color]}:#{line[:label]}"
            end
            result
        end

        def append(label, options={})
            @lines.push({ :label => label }.merge(options))
            self
        end

        def graph(label, rrdfile, range)
            info = generate(rrdfile)
	    if ENV['rrdcached_sock'] != nil && File.exist?(ENV['rrdcached_sock']) then
		info.push("--daemon")
		info.push(ENV['rrdcached_sock'])
	    end
	    if RRD::Wrapper.graph("#{ENV['ROOT']}/public/images/#{File.basename(rrdfile)}.#{label}.png", "--start", (Time.now.to_i - eval(range)).to_s, *info) == false then
		puts RRD::Wrapper.error
	    end
        end

        def merge(label, rrddir, selected, range)
            rrds = selected.split(",")
            result = []

            rrds.each_with_index do |rrd, i|
                lines.each do |line|
                    result.push "DEF:#{line[:label]}#{i}=#{rrddir}/#{rrd}.rrd:#{line[:datasource]}:#{line[:consolid]}"
                end
            end

            @lines.each do |line|
                rpn = "#{line[:label]}0"
                rrds.each_with_index do |rrd, i|
                    if i == 0 then
                        next
                    end
                    rpn = rpn + ",#{line[:label]}#{i}"
                end

                rrds.each_with_index do |rrd, i|
                    if i == 0 then
                        next
                    end
                    rpn = rpn + ",ADDNAN"
                end
                result.push "CDEF:#{line[:label]}=#{rpn}"
            end

            @lines.each do |line|
                result.push "LINE#{line[:width]}:#{line[:label]}#{line[:color]}:#{line[:label]}"
            end

            img_name=Digest::MD5.hexdigest("#{selected}") + ".#{label}.png"
	    if ENV['rrdcached_sock'] != nil && File.exist?(ENV['rrdcached_sock']) then
		result.push("--daemon")
		result.push(ENV['rrdcached_sock'])
	    end
	    if RRD::Wrapper.graph("#{ENV['ROOT']}/public/images/#{img_name}", "--start", (Time.now.to_i - eval(range)).to_s, *result) == false then
		puts RRD::Wrapper.error
	    end
            img_name
        end

        def inspect
            "#Picture"+@lines.map{ |l| l[:label] }.inspect
        end

    end
end
