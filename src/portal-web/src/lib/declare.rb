module Portal
    class Declare

        attr_reader :pictures, :datasources
        
        def initialize
            @pictures = {}
            @datasources = {}
        end

        def draw(&block)
            instance_eval &block
        end


        def picture(label, &block)
            @new_pic = Portal::Picture.new
            instance_eval &block
            @pictures[label] = @new_pic
        end

        def line(label)
            ds = @datasources[label]
            @new_pic.append label.to_s, ds.to_option
        end


        def ds(label, &block)
            @new_ds = Portal::DataSource.new
            @new_ds.instance_eval &block
            @datasources[label] = @new_ds
        end

        def inspect
            @pictures.map{ |p| p.inspect }.inspect
        end

    end
end
