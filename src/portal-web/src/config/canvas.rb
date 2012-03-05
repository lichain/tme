Portal::Monitor.settings.canvas.draw do

    ds :Pending do
        source      :NumMsgs82ff1e7ab6ff
        consolid    :LAST
        color       "#ff0000"
		width       2
    end

    ds :Enqueue do 
        source      :NumMsgsInd604c03a7d
        consolid    :LAST
        color       "#111111"
		width       3
    end

    ds :Dequeue do 
        source      :NumMsgsOut352e0a04c 
        consolid    :LAST
        color       "#AAAAAA"
		width       2
    end

    ds :Dropped do 
        source      :NumMsgs4c353d7d1b5b
        consolid    :LAST
        color       "#33FF00"
		width       2
    end

    picture :raw do
        line :Pending
        line :Enqueue
        line :Dequeue
        line :Dropped
    end

    ds :PendingSize do 
        source      :TotalMsgBytes4c10d0
        consolid    :LAST
        color       "#ff0000"
		width       2
    end

    ds :EnqueueSize do 
        source      :MsgBytesInc06976042
        consolid    :LAST
        color       "#111111"
		width       3
    end

    ds :DequeueSize do 
        source      :MsgBytesOutc0d6418d
        consolid    :LAST
        color       "#AAAAAA"
		width       2
    end

    picture :size do
        line :PendingSize
        line :EnqueueSize
        line :DequeueSize
    end

    ds :Producers do
        source      :NumProducers327fcd6
        consolid    :LAST
        color       "#111111"
		width       3
    end

    ds :Consumers do
        source      :NumConsumers1bbb666
        consolid    :LAST
        color       "#AAAAAA"
		width       2
    end

    picture :source do
        line :Producers
        line :Consumers
    end

end
