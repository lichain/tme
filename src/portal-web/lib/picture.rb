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
            RRD::Wrapper.graph("#{ENV['ROOT']}/public/images/#{File.basename(rrdfile)}.#{label}.png", "--start", (Time.now.to_i - eval(range)).to_s, *info)
        end

        def inspect
            "#Picture"+@lines.map{ |l| l[:label] }.inspect
        end

    end
end
